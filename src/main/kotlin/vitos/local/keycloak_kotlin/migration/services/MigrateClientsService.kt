package vitos.local.keycloak_kotlin.migration.services

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
import vitos.local.keycloak_kotlin.migration.models.JsonType
import vitos.local.keycloak_kotlin.migration.models.MigrateExchange
import vitos.local.keycloak_kotlin.migration.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants
import vitos.local.keycloak_kotlin.migration.models.ClientExportDto
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.migration.models.ClientImportResponseDto
import vitos.local.keycloak_kotlin.migration.models.ClientListExportDto
import vitos.local.keycloak_kotlin.migration.services.keycloak.KeycloakClientService
import vitos.local.keycloak_kotlin.migration.services.keycloak.KeycloakUserService


/**
 * Сервисный слой, обеспечивающий логику миграции (экспорта и импорта) сущностей Client в Keycloak.
 * Класс управляет полным жизненным циклом миграции клиента, включая его роли, мапперы протоколов,
 * настройки авторизации и сервисные учетные записи.
 *
 * @author Vitaly Belotserkovskii (c) 22.12.2025
 */
@Service
class MigrateClientsService(

    private val migrateService: MigrateCommonService,
    private val migrateRepository: MigrateExchangeRepository,
    private val clientsService: KeycloakClientService,
    private val userService: KeycloakUserService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object: Log()


    /**
     * Выполняет чтение списка всех сущностей Clients для заданной входным параметром области сервисов.
     *
     * @param realm (String): Название области (Realm), откуда производится экспорт.
     * @param clientIds (String): Строка, содержащая список client_id (идентификаторов клиентов), которые необходимо экспортировать.
     *
     * @return статус выполнения, список сервисов Clients - либо сообщение об ошибке
     * 200 OK: Возвращает объект ClientListExportDto со списком найденных клиентов.
     * Также сохраняет JSON-дамп в репозиторий MigrateExchangeRepository.
     * 400 BAD REQUEST: Если параметры пусты или клиенты не найдены.
     * 404 NOT FOUND: Если указанный Realm не существует.
     */
    fun getRealmClient(
        realm: String,
        clientIds: String
    ): ResponseEntity<Any> {

        if (realm.isEmpty() || clientIds.isEmpty()) {
            return ResponseEntity(Constants.INVALID_REALM_OR_CLIENT_ID, HttpStatus.BAD_REQUEST)
        }
        try {
            logger.infoM("Procedure export clients by string [$clientIds] started")
            migrateService.getRealmResource(realm)?.let { realmResource ->

                val clients = ClientListExportDto()
                // цикл для каждого найденного в заданной строке запроса client_id
                migrateService.getStringNameList(clientIds).forEach { clientId ->

                    // собираем полную информацию о конкретном сервисе по client_id
                    val clientExportDto =
                        clientsService.getClientRepresentationByClientId(clientId, realmResource)
                    if (clientExportDto != null) {

                        clients.addSuccess(clientExportDto)
                        logger.infoM("Client [$clientId] has been successfully exported")
                    } else {

                        clients.addNotFound(clientId)
                        logger.infoM("Client [$clientId] not found in realm [$realm]")
                    }
                }
                if (clients.clients.isNotEmpty()) {

                    // если хотя бы один сервис найдем, делаем запись в таблицу БД
                    val jsonAsString = objectMapper.writeValueAsString(clients)
                    val migrateRecord = MigrateExchange(realm, JsonType.CLIENTS, jsonAsString)
                    migrateRepository.save(migrateRecord)

                    // получаем имена экспортированных сервисов для вывода в лог
                    val foundClients =
                        clients.clients.stream().map { it.clientRepresentation?.clientId }?.toList() ?: emptyList()
                    logger.infoM("All found client [$foundClients] have been exported")
                    return ResponseEntity(clients, HttpStatus.OK)
                }
                val message = "Clients in [$realm] not found by string [$clientIds]"
                logger.infoM(message)
                return ResponseEntity(message, HttpStatus.BAD_REQUEST)
            }
            logger.infoM("Realm [$realm] not available")
            return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(
                ex,"Unknown error occurred during exporting clients [$clientIds]"
            )
        }
    }


    /**
     * Выполняет создание или обновление сервиса в Clients для заданной входным параметром области сервисов Realm.
     *
     * @param isAlwaysCreate всегда создавать нового клиента с добавлением параметра stamp
     * @param realm заданный в параметрах запроса название области сервисов (обязательный)
     * @param stamp заданный в параметрах запроса штамп модификации имени (если не задан, используется временная метка)
     * @param importClients список экспортных сущностей импортируемых сервисов
     * @return статус выполнения или сообщение об ошибке
     */
    fun createOrUpdateRealmClient(

        realm: String,
        stamp: String?,
        isAlwaysCreate: Boolean,
        importClients: ClientListExportDto
    ): ResponseEntity<Any> {

        val importedClientExportDtoList = importClients.clients
        if (realm.isEmpty() || importedClientExportDtoList.isEmpty()) {
            return ResponseEntity(Constants.INVALID_REALM_OR_CLIENT_ID, HttpStatus.BAD_REQUEST)
        }
        try {
            logger.infoM("Procedure creating | updating clients im [$realm] started")
            migrateService.getRealmResource(realm)?.let { realmResource ->

                val importResponseDto = ClientImportResponseDto()
                importedClientExportDtoList.forEach { importedClientExportDto ->
                    importedClientExportDto.clientRepresentation?.let { importClientRepresentation ->

                        val clientId = importClientRepresentation.clientId
                        // давайте узнаем, существует такой сервис
                        val foundClientRepresentation =
                            realmResource.clients().findByClientId(clientId).firstOrNull()

                        if (isAlwaysCreate) {
                            migrateService.setNameModificationStamp(stamp)
                            importClientRepresentation.clientId += "-${migrateService.stamp}-migrated"
                            importClientRepresentation.description += " (migrated)"
                        }

                        val finalClientRepresentation: ClientRepresentation? =
                            if (foundClientRepresentation == null || isAlwaysCreate) {
                                // импортируемого сервиса в realm нет или принудительно создаём новый
                                createClientImported(
                                    importedClientExportDto,
                                    realmResource
                                )
                            } else {
                                // обновляем уже существующий сервис
                                updateClientImported(
                                    importedClientExportDto,
                                    foundClientRepresentation,
                                    realmResource
                                )
                            }
                        if (finalClientRepresentation != null) {
                            logger.infoM("Client [$clientId] has been successfully created|updated")
                            importResponseDto.addSuccess(finalClientRepresentation)
                        } else {
                            logger.infoM("Unknown error during import [$clientId] occurred")
                            importResponseDto.failedImported.add(clientId)
                        }
                    }
                }
                logger.infoM("In realm [$realm] imported [${importResponseDto.successImported}] clients")
                return ResponseEntity(importResponseDto, HttpStatus.OK)
            }
            logger.errorM("Realm [$realm] not available")
            return ResponseEntity(Constants.INVALID_REALM_NOT_FOUND, HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(
                ex, "Unknown error occurred during import clients in [$realm]")
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
            ?: throw IllegalArgumentException(Constants.CLIENT_NOT_CONFIGURED)
        logger.infoM("Procedure updating for [$clientId] started")
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
            clientsService.createOrUpdateClientRoles(clientExportDto, clientResource)
            userService.updateServiceAccountUser(clientExportDto, clientResource, realmResource)
            updateAuthorizationSettings(clientExportDto, clientResource)

            logger.infoM("Client = [$clientId] updated successfully")
            return clientResource.toRepresentation()

        } catch (ex: Exception) {
            logger.errorM("Update of client $clientId failed by [${ex.message}]", ex)
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
            ?: throw IllegalArgumentException(Constants.CLIENT_NOT_CONFIGURED)

        val clientId = importClientRepresentation.clientId
        logger.infoM("Procedure creating for [$clientId] started")
        try {
            importClientRepresentation.id = null
            // сохраняем новые mappers и создаем сервис с пустыми
            val keepMappers = importClientRepresentation.protocolMappers
            // добавляем новый сервис Client в рабочую область
            response = realmResource.clients().create(importClientRepresentation.apply {
                protocolMappers = null
                authorizationSettings = null
                authenticationFlowBindingOverrides = null
            })
            importClientRepresentation.protocolMappers = keepMappers
            // проверяем результат выполнения операции добавления
            if (response.status == HttpStatus.CREATED.value()) {

                // получаем ресурс управления сервисом
                val id: String = CreatedResponseUtil.getCreatedId(response)
                val clientResource = realmResource.clients().get(id)
                logger.infoM("Client client id = [$clientId] successfully created in Realm")

                // добавляем созданному сервису: roles, mappers, системного пользователя
                createOrUpdateClientProtocolMappers(importClientRepresentation, clientResource)
                clientsService.createOrUpdateClientRoles(clientExportDto, clientResource)
                userService.updateServiceAccountUser(clientExportDto, clientResource, realmResource)
                updateAuthorizationSettings(clientExportDto, clientResource)

                response.close()
                logger.infoM("Client [$clientId] created successfully")
                return clientResource.toRepresentation()
            }
        } catch (ex: Exception) {
            logger.error("Exception during creating client $clientId", ex)
            return null
        } finally {
            response?.close()
        }
        logger.infoM("Creating client [$clientId] failed")
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

        val importProtocolMappers = importClientRepresentation.protocolMappers ?: emptyList()
        val foundProtocolMappers = clientResource.protocolMappers?.mappers?.associateBy { it.name } ?: emptyMap()

        try {
            if (importProtocolMappers.isNotEmpty()) {
                importProtocolMappers.forEach { importMapper ->

                    val name = importMapper.name
                    if (foundProtocolMappers.containsKey(name)) {
                        // mappers найден в сервисе - нужно обновить
                        val id = foundProtocolMappers[name]!!.id
                        clientResource.protocolMappers.update(id, importMapper.apply { this.id = id })
                        logger.infoM("ProtocolMapper name = [$name] updated for client id = $clientId")
                    } else {
                        // mappers не найден в сервисе - создаем новый
                        clientResource.protocolMappers.createMapper(importMapper.apply { this.id = null })
                        logger.infoM("ProtocolMapper name = [$name] created for client id = $clientId")
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
                "Mapping of ProtocolMappers for client: [$clientId] failed by: ${ex.message}", ex)
        }
    }

}

