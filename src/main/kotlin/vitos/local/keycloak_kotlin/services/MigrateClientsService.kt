package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.FATAL_ERROR
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_OR_CLIENT_ID
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
        return ResponseEntity(INVALID_REALM_OR_CLIENT_ID, HttpStatus.BAD_REQUEST)
    }


    /**
     * Выполняет создание или обновление сервиса в Clients для заданной входным параметром области сервисов Realm.
     *
     * @param realm название области сервисов
     * @param clientExportDto экспортная сущность нового сервиса
     * @return статус выполнения или сообщение об ошибке
     */
    fun createOrUpdateRealmClient(
        realm: String,
        clientExportDto: ClientExportDto
    ): ResponseEntity<Any> {

        val clientRepresentation = clientExportDto.clientRepresentation
        if (realm.isNotEmpty() && clientRepresentation != null) {
            try {
                val realmResource = migrateService.getRealmResource(realm)
                if (realmResource != null) {

                    var finalClientRepresentation: ClientRepresentation? = null
                    // определяем, существует уже такой сервис в заданной области
                    val foundClientRepresentation: ClientRepresentation? =
                        realmResource.clients().findByClientId(clientRepresentation.clientId).firstOrNull()

                    if (foundClientRepresentation == null) {
                        // создаем новый сервис
                        finalClientRepresentation = createClient(clientExportDto, realmResource)
                    } else {
                        // обновляем существующий сервис
                        val id = foundClientRepresentation.id
                        val clientResource = realmResource.clients().get(id)

                        clientRepresentation.id = id
                        clientResource.update(clientRepresentation)
                    }
                    if (finalClientRepresentation != null) {
                        return ResponseEntity(finalClientRepresentation, HttpStatus.OK)
                    }
                    logger.error("createOrUpdateRealmClient() :: final client Representation = null")
                    return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
                }
            } catch (ex: Exception) {
                return migrateService.writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        return ResponseEntity(INVALID_REALM_OR_CLIENT_ID, HttpStatus.BAD_REQUEST)
    }


    /**
     * Выполняет создание нового сервиса в области realm
     *
     * @param clientExportDto экспортная сущность нового сервиса
     * @param realmResource ресурс управления областью realm
     * @return сущность нового, созданного Client, или null в случае ошибки
     */
    private fun createClient(
        clientExportDto: ClientExportDto,
        realmResource: RealmResource
    ): ClientRepresentation? {

        val clientRepresentation = clientExportDto.clientRepresentation!!
        try {
            // добавляем новый сервис Client в рабочую область
            clientRepresentation.id = null
            val response = realmResource.clients().create(clientRepresentation)

            // проверяем успешной выполнения операции добавления
            if (response.status == HttpStatus.CREATED.value()) {

                // получаем ресурс управления сервисом
                val id: String = CreatedResponseUtil.getCreatedId(response)
                val clientResource = realmResource.clients().get(id)

                // добавляем роли созданному сервису и обновляем системного пользователя
                createOrUpdateClientRoles(clientExportDto, clientResource)
                updateServiceAccountUser(clientResource, clientExportDto, realmResource)

                return clientResource.toRepresentation()
            }
        } catch (ex: Exception) {
            logger.error("Creating client representation failed for ${clientRepresentation.clientId}", ex)
        }
        return null
    }


    private fun updateServiceAccountUser(
        clientResource: ClientResource,
        clientExportDto: ClientExportDto,
        realmResource: RealmResource
    ) {
        val serviceAccountUser: UserRepresentation? = clientResource.serviceAccountUser
        val serviceAccountUserExport: UserRepresentation? = clientExportDto.serviceAccountUser
        if (serviceAccountUser != null && serviceAccountUserExport != null) {
            try {
                val userId = serviceAccountUser.id
                serviceAccountUserExport.id = userId
                val userResource = realmResource.users().get(userId)
                userResource?.update(serviceAccountUserExport)
            } catch (ex: Exception) {
                logger.error("updateServiceAccountUser() :: failed to update serviceAccountUser = ${serviceAccountUser.username}", ex)
            }
        }
    }


    /**
     * Выполняет создание или обновление ролей, назначенной для сервиса Client
     *
     * @param clientExportDto экспортная сущность нового сервиса
     * @param clientResource ресурс управления сервисом Client
     */
    private fun createOrUpdateClientRoles(
        clientExportDto: ClientExportDto,
        clientResource: ClientResource
    ) {
        try {
            val foundRoles = clientResource.roles()
                    .list(0, Integer.MAX_VALUE).associateBy { it.name }.toMap()

            clientExportDto.clientRoles?.forEach { role ->
                if (!foundRoles.containsKey(role.name)) {
                    role.id = null
                    role.containerId = null
                    clientResource.roles().create(role)
                } else {
                    val roleResource = clientResource.roles().get(role.name)
                    foundRoles[role.name]?.let { roleRepresentation ->
                        roleRepresentation.description = role.description
                        roleResource.update(roleRepresentation)
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("CreateOrUpdateClientRoles() :: failed creating roles for ${clientExportDto.clientRepresentation!!.name}", ex)
        }
    }


    /**
     * Формирует обогащенную несколькими запросами сущность сервиса Client Keycloak
     *
     * @param clientId наименование сервиса
     * @param realmResource ресурс управления областью сервисов
     * @return сущность ClientRepresentation обогащенная данными авторизации
     */
    private fun getClientRepresentationByClientId(
        clientId: String,
        realmResource: RealmResource
    ): ClientExportDto? {

        try {
            val clientRepresentation = realmResource.clients().findByClientId(clientId).firstOrNull()
            if (clientRepresentation != null) {

                val clientResource = realmResource.clients().get(clientRepresentation.id)
                clientRepresentation.protocolMappers = clientResource.protocolMappers.mappers
                //client.authorizationSettings = clientResource.authorization().settings
                //client.authorizationSettings.policies = clientResource.authorization().policies().policies()
                //client.authorizationSettings.scopes = clientResource.authorization().scopes().scopes()
                //client.authorizationSettings.resources = clientResource.authorization().resources().resources()

                val clientExportDto = ClientExportDto()
                clientExportDto.clientRepresentation = clientRepresentation
                clientExportDto.exportSettings = clientResource.authorization().exportSettings()
                clientExportDto.clientRoles = clientResource.roles().list()
                clientExportDto.serviceAccountUser =
                    getServiceAccount(clientResource.serviceAccountUser, realmResource)

                return clientExportDto
            }
        } catch (ex: Exception) {
            logger.error("Error while getting client representation: ${ex.message}, cause: ${ex.cause}", ex)
        }
        return null
    }


    /**
     * Выполняет обогащение сущности сервисного пользователя. Добавляет роли и группы
     *
     * @param userRepresentation базовая сущность сервисного пользователя
     * @param realmResource ресурс управления областью сервисов
     * @return обогащенную сущность сервисного пользователя или ничего
     */
    private fun getServiceAccount(
        userRepresentation: UserRepresentation?,
        realmResource: RealmResource
    ): UserRepresentation? {

        if (userRepresentation != null) {
            try {
                realmResource.users().get(userRepresentation.id)?.let { userResource ->

                    val clientRoles = mutableMapOf<String, List<String>>()
                        userResource.roles().all.clientMappings.map { (key, value) ->
                            clientRoles[key] = value.mappings.map { it.name }.toList()
                        }
                    val realmRoles = userResource.roles().all.realmMappings.map { it.name }.toList()
                    val groups =
                        userResource.groups(0, Integer.MAX_VALUE).map { it.path }.toList()

                    userRepresentation.realmRoles = realmRoles
                    userRepresentation.clientRoles = clientRoles
                    userRepresentation.groups = groups

                    return userRepresentation
                }
            } catch (ex: Exception) {
                logger.error("Error while getting user representation: ${ex.message}", ex)
            }
        }
        return null
    }
}

