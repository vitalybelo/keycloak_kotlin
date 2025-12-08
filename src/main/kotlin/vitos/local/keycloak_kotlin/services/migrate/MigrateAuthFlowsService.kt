package vitos.local.keycloak_kotlin.services.migrate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.AuthenticationExecutionExportRepresentation
import org.keycloak.representations.idm.AuthenticationExecutionRepresentation
import org.keycloak.representations.idm.AuthenticationFlowRepresentation
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.migrate.JsonType
import vitos.local.keycloak_kotlin.models.migrate.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_OR_FLOW_NAME
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NAME
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_FLOW
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.models.migrate.CollectFlowDto
import vitos.local.keycloak_kotlin.models.migrate.ImportFlowDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * Сервисный слой для обеспечения методов миграции потоков аутентификации
 * @author Vitaly Belotserkovskii 27.11.2025
 */
@Service
class MigrateAuthFlowsService(

    private val migrateService: MigrateCommonService,
    private val migrateRepository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    private var aliasCopyStamp: String? = "STAMP"

    companion object: Log() {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy-HHmm")
    }


    /**
     * Выполняет чтение сущностей потока аутентификации realm, заданного параметром
     *
     * @param realm название области сервисов
     * @param alias название потока аутентификации
     * @return статус выполнения, сущность настроек или сообщение об ошибке
     */
    fun getRealmAuthenticationFlow(
        realm: String,
        alias: String
    ): ResponseEntity<Any> {

        if (realm.isEmpty() || alias.isEmpty()) {
            return ResponseEntity(INVALID_REALM_OR_FLOW_NAME, HttpStatus.BAD_REQUEST)
        }
        try {
            migrateService.getRealmResource(realm)?.let { realmResource ->

                logger.infoM("Start collect subflows and configuration for :: $alias")
                val flowRepresentation = realmResource.flows().flows
                    .stream().filter { it.alias.equals(alias,true) }
                    .findFirst().orElse(null)

                if (flowRepresentation != null) {

                    logger.infoM("Flow :: $alias :: found :: continue collect executors")
                    val authenticationFlowsExport = CollectFlowDto()

                    collectAuthenticationSubFlows(
                        flowRepresentation,
                        authenticationFlowsExport,
                        realmResource)

                    val exportFlowDto = authenticationFlowsExport.getExportDto()
                    val jsonAsString = objectMapper.writeValueAsString(exportFlowDto)
                    val migrateRecord = MigrateExchange(realm, JsonType.AUTH_FLOWS, jsonAsString)
                    migrateRepository.save(migrateRecord)

                    logger.infoM("Successfully received flow \"$alias\" configuration")
                    return ResponseEntity(exportFlowDto, HttpStatus.OK)
                }
                return ResponseEntity("Flow alias = \"$alias\" not found",HttpStatus.NOT_FOUND)
            }
            return ResponseEntity("Realm name = \"$realm\" not found",HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            logger.errorM("Failed to get flow configuration for \"$alias\"", ex)
            return migrateService.writeErrorLoggerWithTextAndStatus(ex)
        }
    }


    /**
     * Выполняет создание нового потока аутентификации realm (копию переданного в параметрах)
     *
     * @param realm название области сервисов
     * @param importFlowDto импортируемый dto класс потока аутентификации
     * @return статус выполнения, список сущностей потоков или сообщение об ошибке
     */
    fun createRealmAuthenticationFlow(

        realm: String,
        importFlowDto: ImportFlowDto
    ): ResponseEntity<Any> {

        aliasCopyStamp = " ${LocalDateTime.now().format(FORMATTER)}"
        val importedRootFlow = findTopLevelFlow(importFlowDto)
        if (importFlowDto.authenticationFlows.isNullOrEmpty() || importedRootFlow == null) {
            return ResponseEntity(INVALID_FLOW, HttpStatus.BAD_REQUEST)
        }
        try {
            migrateService.getRealmResource(realm)?.let { realmResource ->

                createAuthenticationFlow(
                    importedRootFlow,
                    realmResource
                )?.let { createdRootFlow ->
                    createAuthenticationFlowEnvironment(
                        importedRootFlow,
                        createdRootFlow,
                        realmResource,
                        importFlowDto
                    )
                }
                return ResponseEntity("Created flow successfully", HttpStatus.OK)
            }
            return ResponseEntity(INVALID_REALM_NAME, HttpStatus.NOT_FOUND)
        } catch (ex: Exception) {
            logger.errorM("Failed to create flow configuration for \"$realm\"", ex)
            return migrateService.writeErrorLoggerWithTextAndStatus(ex)
        } finally {

        }
    }

    /**
     * Выполняет создание потока аутентификации. Добавляет к нему executions и конфигурации - если они имеются
     * в составе потока. Рекурсивно создаются вложенные потоки наполнением шагами и конфигурациями и так далее.
     *
     * @param justCreatedParentFlow импортная сущность потока, для которого создается окружение
     * @param justCreatedParentFlow вновь созданная сущность потока, для которого создается окружение
     * @param adminRealmResource административный ресурс управления областью сервисов
     * @param importFlowDto импортируемый dto класс потока аутентификации
     */
    private fun createAuthenticationFlowEnvironment(

        importedParentFlow: AuthenticationFlowRepresentation,
        justCreatedParentFlow: AuthenticationFlowRepresentation,
        adminRealmResource: RealmResource,
        importFlowDto: ImportFlowDto

    ) {
        val parentFlowId = justCreatedParentFlow.id
        val parentFlowAlias = justCreatedParentFlow.alias

        importedParentFlow.authenticationExecutions?.forEach { execution ->
            try {
                if (!execution.isAuthenticatorFlow) {
                    // здесь создаем исполняемый шаг и добавляем конфигурацию, если необходимо
                    createAuthenticationExecution(
                        null,
                        parentFlowId,
                        execution, adminRealmResource, importFlowDto
                    )
                } else {
                    // находим вложенный поток, создаем его, назначаем шаг и отправляемся в рекурсию
                    receiveImportedFlow(execution.flowAlias, importFlowDto)?.let { importedFlow ->
                        createAuthenticationFlow(
                            importedFlow, adminRealmResource
                        )?.let { createdFlow ->
                            createAuthenticationExecution(
                                createdFlow.id, parentFlowId,
                                execution, adminRealmResource, importFlowDto
                            )?.let {
                                createAuthenticationFlowEnvironment(
                                    importedFlow,
                                    createdFlow,
                                    adminRealmResource,
                                    importFlowDto
                                )
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.errorM(
                    "Failed to create execution = [$execution] in flow [$parentFlowAlias]", ex
                )
            }
        }
    }


    /**
     * @return сущность вложенного потока, или null
     */
    private fun receiveImportedFlow(
        flowAlias: String,
        importFlowDto: ImportFlowDto
    ): AuthenticationFlowRepresentation? {

        importFlowDto.authenticationFlows?.firstOrNull { it.alias.equals(flowAlias) }?.let { return it }
        return null
    }


    /**
     * Создает исполняемый шаг для потока аутентификации - execution.
     *
     * @param flowId идентификатор потока, если шаг это вложенный поток
     * @param parentFlowId идентификатор потока, для которого создается execution
     * @param importedExecution экспортная сущность исполняемого шага
     * @param realmResource административный ресурс управления областью сервисов
     * @param importFlowDto импортируемый dto класс потока аутентификации
     * @return true in success
     */
    private fun createAuthenticationExecution(

        flowId: String?,
        parentFlowId: String?,
        importedExecution: AuthenticationExecutionExportRepresentation,
        realmResource: RealmResource,
        importFlowDto: ImportFlowDto

    ): AuthenticationExecutionRepresentation? {

        var response: Response? = null
        val authenticator = importedExecution.authenticator ?: importedExecution.flowAlias
        try {
            val execution = createExecutionRepresentation(importedExecution)
            execution.flowId = flowId
            execution.parentFlow = parentFlowId
            response = realmResource.flows().addExecution(execution)

            if (response?.status == HttpStatus.CREATED.value()) {
                CreatedResponseUtil.getCreatedId(response)?.let { id ->

                    // шаг создан успешно, теперь если в нем была конфигурация, необходимо ее добавить
                    val createdExecution = realmResource.flows().getExecution(id)
                    logger.infoM("Successfully created authentication execution = [$authenticator]")

                    importedExecution.authenticatorConfig?.let { configAlias ->
                        // нужно найти сущность конфигурации в импорте и создать config для нового шага
                        receiveImportedConfig(configAlias, importFlowDto)?.let {
                            authenticationConfig ->
                            authenticationConfig.id = null
                            authenticationConfig.alias += aliasCopyStamp
                            realmResource.flows().newExecutionConfig(
                                createdExecution.id, authenticationConfig
                            )
                            logger.infoM("Successfully created configuration = [$configAlias] for execution [$authenticator]")
                        }
                    }
                    return createdExecution
                }
            }
            logger.warnM("""
                Creating of execution = [$authenticator] is failed with code = [${response.status}]
            """.trimIndent())

        } catch (ex: Exception) {
            logger.errorM(
                "Failed to create execution = [$authenticator] for flow = [$parentFlowId]",
                ex
            )
        } finally {
            response?.close()
        }
        return null
    }


    /**
     * Ищет в экспортной сущности конфигурацию по названию аутентификатора
     *
     * @param authenticatorConfigName
     * @param importFlowDto
     * @return сущность найденной конфигурации
     */
    private fun receiveImportedConfig(
        authenticatorConfigName: String,
        importFlowDto: ImportFlowDto
    ): AuthenticatorConfigRepresentation? {

        importFlowDto.authenticatorConfigs
            ?.firstOrNull { it.alias.equals(authenticatorConfigName,true) }
            ?.let { return it }
        return null
    }


    /**
     * Инициализирует сущность для создания исполняемого шага в потоке аутентификации
     *
     * @param authenticationExecution экспортная сущность шага
     * @return модель сущности шага для метода create
     */
    private fun createExecutionRepresentation(
        authenticationExecution: AuthenticationExecutionExportRepresentation
    ): AuthenticationExecutionRepresentation {

        return AuthenticationExecutionRepresentation().apply {
            this.authenticator = authenticationExecution.authenticator
            this.isAuthenticatorFlow = authenticationExecution.isAuthenticatorFlow
            this.requirement = authenticationExecution.requirement
            this.priority = authenticationExecution.priority
            /* оставляю для предков, это поле нужно всегда обнулять для создания
            this.authenticatorConfig = authenticationExecution.authenticatorConfig
            */
        }
    }


    /**
     * Выполняет создание потока аутентификации. API Keycloak при создании возвращает ответ выполнения
     *
     * @param authenticationFlowRepresentation сущность потока, для которого создается окружение
     * @param adminRealmResource административный ресурс управления областью сервисов
     * @return сущность созданного потока аутентификации
     */

    private fun createAuthenticationFlow(
        authenticationFlowRepresentation: AuthenticationFlowRepresentation,
        adminRealmResource: RealmResource
    ): AuthenticationFlowRepresentation? {

        var response: Response? = null
        val flowAlias = authenticationFlowRepresentation.alias
        try {
            authenticationFlowRepresentation.id = null
            authenticationFlowRepresentation.alias += aliasCopyStamp
            response = adminRealmResource.flows().createFlow(authenticationFlowRepresentation)

            if (response?.status == HttpStatus.CREATED.value()) {

                val flowId = CreatedResponseUtil.getCreatedId(response)
                logger.infoM("Flow :: [$flowAlias] created successfully :: flow id = $flowId")
                return adminRealmResource.flows().getFlow(flowId)
            }
            logger.warnM(
                """
                | Creating authentication flow [$flowAlias] failed with code = ${response.status}
                """.trimIndent())

        } catch (ex: Exception) {
            logger.errorM("Failed to create flow :: [$flowAlias] :: by ${ex.message}", ex)
        } finally {
            response?.close()
        }
        return null
    }


    /**
     * @return сущность верхне-уровневого потока аутентификации (корневого)
     */
    private fun findTopLevelFlow(
        importFlowDto: ImportFlowDto
    ): AuthenticationFlowRepresentation? {

        importFlowDto.authenticationFlows?.firstOrNull { it.isTopLevel }?.let { return it }
        return null
    }



    /**
     * Рекурсивный метод, позволяющий собрать конфигурации и под-потоки для основного flow
     *
     * @param rootFlowRepresentation сущность потока, для которого выполняется сбор данных
     * @param collectFlowDto экспортная коллекционная сущность
     * @param realmResource ресурс управления областью сервисов
     */
    private fun collectAuthenticationSubFlows(

        rootFlowRepresentation: AuthenticationFlowRepresentation,
        collectFlowDto: CollectFlowDto,
        realmResource: RealmResource
    ) {
        // сохраняем поток в экспортной карте, если его там ещё нет
        collectFlowDto.flowsMap[rootFlowRepresentation.id] = rootFlowRepresentation

        // получаем список "исполнителей" входящих в поток аутентификации
        val flowAlias = rootFlowRepresentation.alias
        val flowExecutions = realmResource.flows().getExecutions(flowAlias)
        if (flowExecutions.isNullOrEmpty()) return // выходим если поток пустой
        logger.infoM("Start collecting authentication flows and configurations for :: [$flowAlias]")

        flowExecutions.forEach { execution ->

            try {
                execution.authenticationConfig?.let { id ->
                    if (!collectFlowDto.configsMap.contains(id)) {
                        realmResource.flows().getAuthenticatorConfig(id)?.let { config ->
                            collectFlowDto.configsMap[id] = config
                            logger.infoM("Successfully collected configuration :: [${config.alias}]")
                        }
                    }
                }
                execution.flowId?.let { id ->
                    if (!collectFlowDto.flowsMap.contains(id)) {
                        realmResource.flows().getFlow(id)?.let { flow ->
                            collectAuthenticationSubFlows(flow, collectFlowDto, realmResource)
                            logger.infoM("Successfully collected authentication flow [${flow.alias}]")
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.errorM("Collecting configuration for $flowAlias is failed", ex)
            }
        }
        logger.infoM("Finish collecting authentication flows and configurations for :: [$flowAlias]")
    }



}

