package vitos.local.keycloak_kotlin.migration.services

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
import vitos.local.keycloak_kotlin.migration.models.JsonType
import vitos.local.keycloak_kotlin.migration.models.MigrateExchange
import vitos.local.keycloak_kotlin.migration.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.migration.models.CollectFlowDto
import vitos.local.keycloak_kotlin.migration.models.FlowImportDto
import java.util.stream.Collectors


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


    companion object: Log()


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
            return ResponseEntity(Constants.INVALID_REALM_OR_FLOW_NAME, HttpStatus.BAD_REQUEST)
        }
        try {
            migrateService.getRealmResource(realm)?.let { realmResource ->

                // получаем список корневых потоков из общего списка
                val rootFlowsList =
                    getRealmAuthenticationFlowList(
                        alias,
                        realmResource
                    )
                // если корневые потоки найдены, выполняем создание копий
                if (!rootFlowsList.isEmpty()) {

                    val authenticationFlowsExport = CollectFlowDto(rootFlowsList.size)
                    rootFlowsList.forEach { rootFlowRepresentation ->

                        logger.infoM("Start collect collect executors and configs for ${rootFlowRepresentation.alias}")
                        collectAuthenticationSubFlows(
                            rootFlowRepresentation,
                            authenticationFlowsExport,
                            realmResource)
                    }
                    logger.infoM("Finished collection flows and configurations")

                    val exportFlowDto = authenticationFlowsExport.getExportDto()
                    val jsonAsString = objectMapper.writeValueAsString(exportFlowDto)
                    val migrateRecord = MigrateExchange(realm, JsonType.AUTH_FLOWS, jsonAsString)
                    migrateRepository.save(migrateRecord)

                    val rootCount = authenticationFlowsExport.rootFlowsCount
                    logger.infoM("Received = $rootCount root flows with executions & configurations for [$alias] successfully")
                    return ResponseEntity(exportFlowDto, HttpStatus.OK)
                }
                return ResponseEntity("Flow alias = [$alias] not found",HttpStatus.NOT_FOUND)
            }
            return ResponseEntity("Realm name = [$realm] not found",HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            logger.errorM("Failed to get flow configuration for [$alias]", ex)
            return migrateService.writeErrorLoggerWithTextAndStatus(ex)
        }
    }

    /**
     * Выполняет поиск всех корневых потоков аутентификации, удовлетворяющих условию поиска по alias.
     * В качестве названия потока, методу можно передать "*" для получения списка всех потоков области,
     * или список имен потоков, разделенных запятой. Названия имен в списке должны точно совпадать
     * с оригинальным названием потока
     *
     * @param flowPattern паттерн поиска потоков аутентификации
     * @param adminRealmResource административный ресурс управления областью сервисов
     * @return список найденных корневых потоков по условию поиска
     */
    private fun getRealmAuthenticationFlowList(

        flowPattern: String,
        adminRealmResource: RealmResource
    ): List<AuthenticationFlowRepresentation> {

        logger.infoM("Start collect root flow and configuration for pattern :: $flowPattern")

        val aliasList =  migrateService.getStringNameList(flowPattern)
        val rootFlowsList = adminRealmResource.flows().flows
            .stream().filter { representation ->
                representation.isTopLevel &&
                        (flowPattern == "*" || aliasList.contains(representation.alias))
            }.collect(Collectors.toList()) ?: emptyList()

        logger.infoM("Totally found = ${rootFlowsList.size} root flows and configurations")
        return rootFlowsList
    }


    /**
     * Выполняет создание нового потока аутентификации realm (копию переданного в параметрах)
     *
     * @param realm название области сервисов
     * @param stamp заданный в параметрах запроса штамп модификации имени
     * @param flowImportDto импортируемый dto класс потока аутентификации
     * @return статус выполнения, список сущностей потоков или сообщение об ошибке
     */
    fun createRealmAuthenticationFlow(

        realm: String,
        stamp: String?,
        flowImportDto: FlowImportDto
    ): ResponseEntity<Any> {

        val importedRootFlows = findTopLevelFlow(flowImportDto)
        if (flowImportDto.authenticationFlows.isNullOrEmpty() || importedRootFlows.isEmpty()) {
            return ResponseEntity(Constants.INVALID_FLOW, HttpStatus.BAD_REQUEST)
        }
        try {
            migrateService.getRealmResource(realm)?.let { realmResource ->

                migrateService.setNameModificationStamp(stamp)
                importedRootFlows.forEach { importedRootFlow ->

                    createAuthenticationFlow(
                        importedRootFlow,
                        realmResource
                    )?.let { createdRootFlow ->
                        createAuthenticationFlowEnvironment(
                            importedRootFlow,
                            createdRootFlow,
                            realmResource,
                            flowImportDto
                        )
                    }
                }
                return ResponseEntity("Flows created successfully", HttpStatus.OK)
            }
            return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.NOT_FOUND)
        } catch (ex: Exception) {
            logger.errorM("Failed to create flow configuration for [$realm]", ex)
            return migrateService.writeErrorLoggerWithTextAndStatus(ex)
        } finally {

        }
    }


    /**
     * Выполняет изменения названия потока аутентификации или конфига, добавляя в него штамп времени
     * @param flowName текущее название потока или конфигурации
     */
    fun createFlowAliasTimeStamped(
        flowName: String
    ): String {

        val migrated = "migrated"
        val migratedTimeStamp = " ${migrateService.stamp} $migrated"
        val lengthTimeStamp = migratedTimeStamp.length
        val lengthFlowName = flowName.length

        if (lengthFlowName > lengthTimeStamp && flowName.endsWith(migrated)) {
            // найден старый фирменный знак миграции, удаляем и заменяем на новый
            val endIndex = lengthFlowName - lengthTimeStamp
            val originalFlowName = flowName.take(endIndex) + migratedTimeStamp
            return originalFlowName
        }
        val firstTimeStamped = flowName + migratedTimeStamp
        return firstTimeStamped
    }


    /**
     * Выполняет создание потока аутентификации. Добавляет к нему executions и конфигурации - если они имеются
     * в составе потока. Рекурсивно создаются вложенные потоки наполнением шагами и конфигурациями и так далее.
     *
     * @param justCreatedParentFlow импортная сущность потока, для которого создается окружение
     * @param justCreatedParentFlow вновь созданная сущность потока, для которого создается окружение
     * @param adminRealmResource административный ресурс управления областью сервисов
     * @param flowImportDto импортируемый dto класс потока аутентификации
     */
    private fun createAuthenticationFlowEnvironment(

        importedParentFlow: AuthenticationFlowRepresentation,
        justCreatedParentFlow: AuthenticationFlowRepresentation,
        adminRealmResource: RealmResource,
        flowImportDto: FlowImportDto

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
                        execution, adminRealmResource, flowImportDto
                    )
                } else {
                    // находим вложенный поток, создаем его, назначаем шаг и отправляемся в рекурсию
                    receiveImportedFlow(execution.flowAlias, flowImportDto)?.let { importedFlow ->
                        createAuthenticationFlow(
                            importedFlow, adminRealmResource
                        )?.let { createdFlow ->
                            createAuthenticationExecution(
                                createdFlow.id, parentFlowId,
                                execution, adminRealmResource, flowImportDto
                            )?.let {
                                createAuthenticationFlowEnvironment(
                                    importedFlow,
                                    createdFlow,
                                    adminRealmResource,
                                    flowImportDto
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
        flowImportDto: FlowImportDto
    ): AuthenticationFlowRepresentation? {

        flowImportDto.authenticationFlows?.firstOrNull { it.alias.equals(flowAlias) }?.let { return it }
        return null
    }


    /**
     * Создает исполняемый шаг для потока аутентификации - execution.
     *
     * @param flowId идентификатор потока, если шаг это вложенный поток
     * @param parentFlowId идентификатор потока, для которого создается execution
     * @param importedExecution экспортная сущность исполняемого шага
     * @param realmResource административный ресурс управления областью сервисов
     * @param flowImportDto импортируемый dto класс потока аутентификации
     * @return true in success
     */
    private fun createAuthenticationExecution(

        flowId: String?,
        parentFlowId: String?,
        importedExecution: AuthenticationExecutionExportRepresentation,
        realmResource: RealmResource,
        flowImportDto: FlowImportDto

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
                        receiveImportedConfig(configAlias, flowImportDto)?.let {
                            authenticationConfig ->
                            authenticationConfig.id = null
                            authenticationConfig.alias = createFlowAliasTimeStamped(configAlias)
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
     * @param flowImportDto
     * @return сущность найденной конфигурации
     */
    private fun receiveImportedConfig(
        authenticatorConfigName: String,
        flowImportDto: FlowImportDto
    ): AuthenticatorConfigRepresentation? {

        flowImportDto.authenticatorConfigs
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
            authenticationFlowRepresentation.isBuiltIn = false
            authenticationFlowRepresentation.alias = createFlowAliasTimeStamped(flowAlias)
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
     * @return список корневых сущностей верхне-уровневого потока аутентификации
     */
    private fun findTopLevelFlow(
        flowImportDto: FlowImportDto
    ): List<AuthenticationFlowRepresentation> {

        return flowImportDto.authenticationFlows?.filter { it.isTopLevel } ?: emptyList()
    }


    /**
     * Рекурсивный метод, позволяющий собрать конфигурации и под-потоки для основного flow
     *
     * @param authenticationRootFlow сущность потока, для которого выполняется сбор данных
     * @param collectFlowDto экспортная коллекционная сущность
     * @param adminRealmResource ресурс управления областью сервисов
     */
    private fun collectAuthenticationSubFlows(

        authenticationRootFlow: AuthenticationFlowRepresentation,
        collectFlowDto: CollectFlowDto,
        adminRealmResource: RealmResource
    ) {
        // сохраняем поток в экспортной карте, если его там ещё нет
        collectFlowDto.addFlow(authenticationRootFlow)

        // получаем список "исполнителей" входящих в поток аутентификации
        val flowAlias = authenticationRootFlow.alias
        val flowExecutions = adminRealmResource.flows().getExecutions(flowAlias)
        if (flowExecutions.isNullOrEmpty()) return // выходим если поток пустой
        logger.infoM("Start collecting authentication flows and configurations for :: [$flowAlias]")

        flowExecutions.forEach { execution ->

            try {
                execution.authenticationConfig?.let { id ->
                    if (!collectFlowDto.configsMap.contains(id)) {
                        adminRealmResource.flows().getAuthenticatorConfig(id)?.let { config ->
                            collectFlowDto.configsMap[id] = config
                            logger.infoM("Successfully collected configuration :: [${config.alias}]")
                        }
                    }
                }
                execution.flowId?.let { id ->
                    if (!collectFlowDto.flowsMap.contains(id)) {
                        adminRealmResource.flows().getFlow(id)?.let { flow ->
                            collectAuthenticationSubFlows(flow, collectFlowDto, adminRealmResource)
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