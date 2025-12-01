package vitos.local.keycloak_kotlin.services.migrate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.AuthenticationFlowRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.migrate.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NAME
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.models.migrate.CollectFlowDto


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
            return ResponseEntity(INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
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
     * Рекурсивный метод, позволяющий собрать конфигурации и под-потоки для основного flow
     *
     * @param rootFlowRepresentation сущность потока для которого выполняется сбор данных
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

        flowExecutions.forEach { execution ->

            try {
                execution.authenticationConfig?.let { id ->
                    if (!collectFlowDto.configsMap.contains(id)) {
                        realmResource.flows().getAuthenticatorConfig(id)?.let { config ->
                            collectFlowDto.configsMap[id] = config
                        }
                    }
                }
                execution.flowId?.let { id ->
                    if (!collectFlowDto.flowsMap.contains(id)) {
                        realmResource.flows().getFlow(id)?.let { flow ->
                            collectAuthenticationSubFlows(flow, collectFlowDto, realmResource)
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.errorM("Collecting configuration for $flowAlias is failed", ex)
            }
        }
    }



}

