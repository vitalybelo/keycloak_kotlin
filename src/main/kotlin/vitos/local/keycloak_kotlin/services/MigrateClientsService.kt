package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.resource.RealmResource
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_OR_CLIENT_NAMES
import vitos.local.keycloak_kotlin.models.ClientExportDto


/**
 * Сервисный слой для обеспечения методов миграции Clients
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateClientsService(

    private val migrateService: MigrateCommonService,
    private val migrateRepository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateClientsService::class.java)
    }


    /**
     * Выполняет чтение списка всех сущностей Clients для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов
     * @param clientId название сервиса
     * @return статус выполнения, список сервисов Clients - либо сообщение об ошибке
     */
    fun getRealmClients(realm: String, clientId: String): ResponseEntity<Any> {

        if (realm.isNotEmpty() && clientId.isNotEmpty()) {
            try {
                val realmResource = migrateService.getRealmResource(realm)
                if (realmResource != null) {

                    getClientRepresentationByClientId(clientId, realmResource)?.let { client ->

                        val jsonAsString = objectMapper.writeValueAsString(client)
                        val migrateRecord = MigrateExchange(realm, JsonType.CLIENTS, jsonAsString)
                        migrateRepository.save(migrateRecord)

                        return ResponseEntity(client, HttpStatus.OK)
                    }
                    return ResponseEntity(
                        "Client: $clientId not found in realm = $realm",
                        HttpStatus.NOT_FOUND
                    )
                }
            } catch (ex: Exception) {
                return migrateService.writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        return ResponseEntity(INVALID_REALM_OR_CLIENT_NAMES, HttpStatus.BAD_REQUEST)
    }


    /**
     * Формирует обогащенную несколькими запросами сущность сервиса Client Keycloak
     *
     * @param clientId наименование сервиса
     * @param realmResource ресурс управления областью сервисов
     * @return сущность ClientRepresentation обогащенная данными авторизации
     */
    fun getClientRepresentationByClientId(
        clientId: String,
        realmResource: RealmResource
    ): ClientExportDto? {

        try {
            val client = realmResource.clients().findByClientId(clientId).firstOrNull()
            if (client != null) {

                val clientResource = realmResource.clients().get(client.id)
                client.protocolMappers = clientResource.protocolMappers.mappers
                client.authorizationSettings = clientResource.authorization().settings
                client.authorizationSettings.policies = clientResource.authorization().policies().policies()
                client.authorizationSettings.scopes = clientResource.authorization().scopes().scopes()
                client.authorizationSettings.resources = clientResource.authorization().resources().resources()

                val clientExportDto = ClientExportDto()
                clientExportDto.clientRepresentation = client
                clientExportDto.serviceAccountUser = clientResource.serviceAccountUser
                clientExportDto.exportSettings = clientResource.authorization().exportSettings()
                clientExportDto.clientRoles = clientResource.roles().list()

                return clientExportDto
            }
        } catch (ex: Exception) {
            logger.error("Error while getting client representation: ${ex.message}, cause: ${ex.cause}", ex)
        }
        return null
    }

}

