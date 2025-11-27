package vitos.local.keycloak_kotlin.services.migrate

import jakarta.ws.rs.core.Response
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NAME
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_OR_CLIENT_ID
import vitos.local.keycloak_kotlin.constants.Constants.Companion.CLIENT_NOT_CONFIGURED
import vitos.local.keycloak_kotlin.models.ClientExportDto
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.services.keycloak.KeycloakClientService
import vitos.local.keycloak_kotlin.services.keycloak.KeycloakUserService


/**
 * Сервисный слой для обеспечения методов миграции Clients
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateClientsService(

    private val migrateService: MigrateCommonService,
    private val migrateRepository: MigrateExchangeRepository,
    private val keycloakClientsService: KeycloakClientService,
    private val keycloakUserService: KeycloakUserService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object: Log()


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

                    keycloakClientsService.getClientRepresentationByClientId(clientId, realmResource)?.let { client ->

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

        val importClientRepresentation = clientExportDto.clientRepresentation
        if (realm.isEmpty() || importClientRepresentation == null) {
            // входные параметры не заданы корректно, выходим с ошибкой и статусом 400
            return ResponseEntity(INVALID_REALM_OR_CLIENT_ID, HttpStatus.BAD_REQUEST)
        }
        try {
            val realmResource = migrateService.getRealmResource(realm)
            if (realmResource != null) {

                val clientId = importClientRepresentation.clientId
                var finalClientRepresentation: ClientRepresentation?
                // определяем, существует уже такой сервис в заданной области
                val foundClientRepresentation =
                    realmResource.clients().findByClientId(clientId).firstOrNull()

                finalClientRepresentation =
                    if (foundClientRepresentation == null) {
                        // импортируемого сервиса в realm нет, поэтому создаем новый
                        createClientImported(
                            clientExportDto,
                            realmResource
                        )
                    } else {
                        // обновляем существующий сервис
                        updateClientImported(
                            clientExportDto,
                            foundClientRepresentation,
                            realmResource
                        )
                    }
                if (finalClientRepresentation != null) {
                    return ResponseEntity(finalClientRepresentation, HttpStatus.OK)
                }
            }
            logger.errorM("Realm = \"$realm\" not found in Keycloak :: return")
            return ResponseEntity(INVALID_REALM_NAME, HttpStatus.NOT_FOUND)
        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(ex)
        }
    }


    /**
     * Выполняет обновление сервиса в Clients для заданной входным параметром области сервисов Realm.
     *
     * @param clientExportDto экспортная сущность нового сервиса
     * @param foundClientRepresentation существующая сейчас сущность сервиса
     * @param realmResource ресурс управления областью realm
     * @return сущность обновленного сервиса
     */
    private fun updateClientImported(

        clientExportDto: ClientExportDto,
        foundClientRepresentation: ClientRepresentation,
        realmResource: RealmResource
    ): ClientRepresentation? {

        val clientId = foundClientRepresentation.clientId
        val importClientRepresentation = clientExportDto.clientRepresentation
            ?: throw IllegalArgumentException(CLIENT_NOT_CONFIGURED)
        try {
            val id = foundClientRepresentation.id
            val clientResource = realmResource.clients().get(id)

            // сохраняем новые mappers и обновляем сервис пока со старыми mappers
            importClientRepresentation.id = id
            val keepMappers = importClientRepresentation.protocolMappers
            clientResource.update(importClientRepresentation.apply {
                protocolMappers = foundClientRepresentation.protocolMappers
            })
            // обновляем mappers, роли, сущность системного пользователя для сервиса
            importClientRepresentation.protocolMappers = keepMappers
            createOrUpdateClientProtocolMappers(importClientRepresentation, clientResource)
            keycloakClientsService.createOrUpdateClientRoles(clientExportDto, clientResource)
            keycloakUserService.updateServiceAccountUser(clientExportDto, clientResource, realmResource)
            updateAuthorizationSettings(clientExportDto, clientResource)

            logger.infoM("Client = \"$clientId\" updated successfully")
            return clientResource.toRepresentation()

        } catch (ex: Exception) {
            logger.errorM("Update of client $clientId failed by ${ex.message}", ex)
        }
        return null
    }


    /**
     * Выполняет создание нового сервиса в области realm. После успешного создания, добавляются mappers,
     * client роли, системного пользователя, ресурсы, permissions, scopes etc
     *
     * @param clientExportDto экспортная сущность нового сервиса
     * @param realmResource ресурс управления областью realm
     * @return сущность нового, созданного Client, или null в случае ошибки
     */
    private fun createClientImported(

        clientExportDto: ClientExportDto,
        realmResource: RealmResource
    ): ClientRepresentation? {

        var response: Response? = null
        val importClientRepresentation = clientExportDto.clientRepresentation
            ?: throw IllegalArgumentException(CLIENT_NOT_CONFIGURED)
        try {
            importClientRepresentation.id = null
            val clientId = importClientRepresentation.clientId
            val keepMappers = importClientRepresentation.protocolMappers
            // добавляем новый сервис Client в рабочую область
            response = realmResource.clients().create(importClientRepresentation.apply {
                protocolMappers = null
                authorizationSettings = null
            })
            importClientRepresentation.protocolMappers = keepMappers
            // проверяем результат выполнения операции добавления
            if (response.status == HttpStatus.CREATED.value()) {

                // получаем ресурс управления сервисом
                val id: String = CreatedResponseUtil.getCreatedId(response)
                val clientResource = realmResource.clients().get(id)
                logger.infoM("Client client id = \"$clientId\" successfully created in Realm")

                // добавляем созданному сервису: roles, mappers, системного пользователя
                createOrUpdateClientProtocolMappers(importClientRepresentation, clientResource)
                keycloakClientsService.createOrUpdateClientRoles(clientExportDto, clientResource)
                keycloakUserService.updateServiceAccountUser(clientExportDto, clientResource, realmResource)
                updateAuthorizationSettings(clientExportDto, clientResource)

                response.close()
                return clientResource.toRepresentation()
            }
        } catch (ex: Exception) {
            logger.error("Creating client representation failed for ${importClientRepresentation.clientId}", ex)
        } finally {
            response?.close()
        }
        return null
    }


    /**
     * Выполняет обновление настроек авторизации для клиента. Вначале метод удаляет все имеющиеся
     * ресурсы, scopes и политики, затем импортирует эти настройки из экспортной сущности client
     *
     * @param clientExportDto экспортная сущность нового сервиса
     * @param clientResource ресурс управления сервисом Client
     */
    fun updateAuthorizationSettings(

        clientExportDto: ClientExportDto,
        clientResource: ClientResource
    ) {
        val isAuthorizationEnabled =
            clientExportDto.clientRepresentation?.authorizationServicesEnabled ?: false

        if (isAuthorizationEnabled) {
            try {
                val exportSettings = clientExportDto.exportSettings

                // удаление именно в такой последовательности
                deleteAuthorizationResources(clientResource)
                deleteAuthorizationScopes(clientResource)
                deleteAuthorizationPolicies(clientResource)

                clientResource.authorization().importSettings(exportSettings)
            } catch (ex: Exception) {
                val clientId = clientExportDto.clientRepresentation?.clientId ?: "NONAME"
                logger.errorM("Export import settings failed for $clientId", ex)
            }
        }
    }


    /**
     * Удаляет все имеющиеся scopes у client
     * @param clientResource ресурс управления клиентом
     */
    private fun deleteAuthorizationPolicies(clientResource: ClientResource) {
        try {
            val policies = clientResource.authorization()?.policies()?.policies()
            if (policies.isNullOrEmpty()) return
            policies.forEach { policy ->
                clientResource.authorization().policies().client().findById(policy.id).remove()
            }
            logger.infoM("Authorization policies count = ${policies.size} :: deleted  successfully")
        } catch (ex: Exception) {
            logger.errorM("Delete authorization policies failed by ${ex.message}", ex)
        }
    }


    /**
     * Удаляет все имеющиеся scopes у client
     * @param clientResource ресурс управления клиентом
     */
    private fun deleteAuthorizationScopes(clientResource: ClientResource) {
        try {
            val scopes = clientResource.authorization()?.scopes()?.scopes()
            if (scopes.isNullOrEmpty()) return
            scopes.forEach{ scopes ->
                clientResource.authorization().scopes().scope(scopes.id).remove()
            }
            logger.infoM("Authorization scopes count = ${scopes.size} :: deleted  successfully")
        } catch (ex: Exception) {
            logger.errorM("Delete authorization scopes failed by ${ex.message}", ex)
        }
    }


    /**
     * Удаляет все имеющиеся ресурсы у client
     * @param clientResource ресурс управления клиентом
     */
    private fun deleteAuthorizationResources(clientResource: ClientResource) {
        try {
            val resources = clientResource.authorization()?.resources()?.resources()
            if (resources.isNullOrEmpty()) return
            resources.forEach { resource ->
                clientResource.authorization().resources().resource(resource.id).remove()
            }
            logger.infoM("Authorization resources count = ${resources.size} :: deleted  successfully")
        } catch (ex: Exception) {
            logger.errorM("Delete authorization resources failed by ${ex.message}", ex)
        }
    }


    /**
     * Добавляет и обновляет protocol mappers для Clients, заданного ресурсом управления
     *
     * @param importClientRepresentation новая сущность со списком протоколов
     * @param clientResource ресурс управления сервисом
     */
    private fun createOrUpdateClientProtocolMappers(

        importClientRepresentation: ClientRepresentation,
        clientResource: ClientResource,
    ) {
        val clientId = importClientRepresentation.clientId

        val importProtocolMappers =
            importClientRepresentation.protocolMappers ?: emptyList()
        val foundProtocolMappers =
            clientResource.protocolMappers?.mappers?.associateBy { it.name } ?: emptyMap()

        try {
            if (importProtocolMappers.isNotEmpty()) {
                importProtocolMappers.forEach { importMapper ->

                    val name = importMapper.name
                    if (foundProtocolMappers.containsKey(name)) {
                        // mappers найден в сервисе - нужно обновить
                        val id = foundProtocolMappers[name]!!.id
                        clientResource.protocolMappers.update(id, importMapper.apply { this.id = id })
                        logger.infoM("ProtocolMapper name = \"$name\" updated for client id = $clientId")
                    } else {
                        // mappers не найден в сервисе - создаем новый
                        clientResource.protocolMappers.createMapper(importMapper.apply { this.id = null })
                        logger.infoM("ProtocolMapper name = \"$name\" created for client id = $clientId")
                    }
                }
                // теперь нужно удалить mappers, которые уже не актуальны для clients
                foundProtocolMappers.map { it.value }.forEach { protocolMapper ->
                    val name = protocolMapper.name
                    if (importProtocolMappers.stream().noneMatch { it.name.equals(name) }) {
                        clientResource.protocolMappers.delete(protocolMapper.id)
                    }
                }
            } else {
                // нужно удалить protocol mappers которые есть на данный момент у сервиса
                if (foundProtocolMappers.isNotEmpty()) {
                    foundProtocolMappers.forEach { protocolMapper ->
                        clientResource.protocolMappers.delete(protocolMapper.value.id)
                    }
                }
            }
        } catch (ex: Exception) {
            logger.errorM(
                "Mapping of ProtocolMappers for client: \"$clientId\" failed by: ${ex.message}", ex)
        }
    }

}

