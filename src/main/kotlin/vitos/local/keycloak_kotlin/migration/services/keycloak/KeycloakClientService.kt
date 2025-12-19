package vitos.local.keycloak_kotlin.migration.services.keycloak

import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.CREATE_CLIENT_DESC
import vitos.local.keycloak_kotlin.constants.Constants.Companion.CREATE_ROLE_DESC
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.migration.models.ClientExportDto


/**
 * Класс управления сервисами в задачах миграции
 * @author Vitaly Belotserkovskii
 */
@Service
class KeycloakClientService {

    companion object : Log()


    /**
     * Выполняет поиск существующего в области сервисов клиента.
     * Если клиент не найден, метод создает нового клиента в области
     *
     * @param clientID название сервиса
     * @param realmResource ресурс управления областью сервисов
     * @return сущность сервиса или null
     */
    fun findOrCreateClient(

        clientID: String,
        realmResource: RealmResource

    ): ClientRepresentation? {

        // пробуем найти клиента в области
        realmResource.clients().findByClientId(clientID).firstOrNull()?.let { return it }
        try {
            realmResource.clients().create(ClientRepresentation().apply {
                clientId = clientID
                description = CREATE_CLIENT_DESC
            })
            return realmResource.clients().findByClientId(clientID).firstOrNull()

        } catch (ex: Exception) {
            logger.errorM("Error of find_|_creating client $clientID :: ${ex.message}", ex)
        }
        return null
    }


    /**
     * Выполняет создание client роли, если при выполнении метода привязки client был найден, но
     * нужная роль в клиенте не найдена.
     *
     * @param roleName название роли, которую нужно создать
     * @param clientResource ресурс управления клиентом области
     * @return сущность созданной роли или null
     */
    fun createClientRoles(

        roleName: String,
        clientResource: ClientResource
    ): RoleRepresentation? {
        try {
            clientResource.roles().create(RoleRepresentation().apply {
                name = roleName
                description = CREATE_ROLE_DESC
            })
            return clientResource.roles().get(roleName).toRepresentation()

        } catch (ex: Exception) {
            logger.errorM("Error of creating client role $roleName :: ${ex.message}", ex)
        }
        return null
    }


    /**
     * Выполняет создание или обновление ролей, назначенной для сервиса Client
     *
     * @param clientExportDto экспортная сущность нового сервиса
     * @param clientResource ресурс управления сервисом Client
     */
    fun createOrUpdateClientRoles(

        clientExportDto: ClientExportDto,
        clientResource: ClientResource
    ) {
        // выполняем проверку на необходимость выполнения присвоения ролей
        val clientId = clientExportDto.clientRepresentation?.clientId
        val importClientRoles = clientExportDto.clientRoles
        if (importClientRoles.isNullOrEmpty() || clientId.isNullOrEmpty()) {
            logger.infoM("Client id = $clientId :: import roles $importClientRoles :: return")
            return
        }

        // получаем список ролей, которые сейчас назначены для client
        val foundClientRoles = clientResource.roles()
            .list(0, Integer.MAX_VALUE)?.associateBy { it.name } ?: emptyMap()

        // начинаем назначать роли для клиента из импортируемой сущности
        importClientRoles.forEach { importClientRole ->
            val name = importClientRole.name
            try {
                if (!foundClientRoles.containsKey(name)) {
                    // создаем новую роль для сервиса
                    clientResource.roles().create(importClientRole.apply {
                        id = null
                        containerId = null
                    })
                    logger.infoM("Role with name = [$name] created successfully for client id = [$clientId]")
                } else {
                    // обновляем существующую роль
                    clientResource.roles().get(name)?.let { roleResource ->
                        val foundRole = foundClientRoles[name]!!
                        roleResource.update(importClientRole.apply {
                            id = foundRole.id
                            containerId = foundRole.containerId
                        })
                        logger.infoM("Role with name = [$name] updated successfully for client id = [$clientId]")
                    }
                }
            } catch (ex: Exception) {
                logger.errorM("Failed creating of [name] for client id = [$clientId]",ex)
            }
        }
        // удаляем роли, который уже не должны быть назначены для Client
        try {
            foundClientRoles.map { it.value }.forEach { clientRole ->
                val name = clientRole.name
                if (importClientRoles.stream().noneMatch { it.name.equals(name) }) {
                    clientResource.roles().deleteRole(name)
                    logger.infoM("Role with name = [$name] deleted successfully")
                }
            }
        } catch (ex: Exception) {
            logger.errorM("Deleting outdated roles failed by ${ex.message}", ex)
        }
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
            val clientRepresentation = realmResource.clients().findByClientId(clientId).firstOrNull()
            if (clientRepresentation != null) {

                val clientExportDto = ClientExportDto(clientRepresentation = clientRepresentation)

                val clientResource = realmResource.clients().get(clientRepresentation.id)
                clientExportDto.clientRoles = clientResource.roles()?.list() ?: emptyList()

                val isAuthorizationEnabled = clientRepresentation.authorizationServicesEnabled ?: false
                if (isAuthorizationEnabled) {
                    clientRepresentation.authorizationSettings = clientResource.authorization().settings
                    clientRepresentation.authorizationSettings.policies = clientResource.authorization().policies().policies()
                    clientRepresentation.authorizationSettings.scopes = clientResource.authorization().scopes().scopes()
                    clientRepresentation.authorizationSettings.resources = clientResource.authorization().resources().resources()
                    clientExportDto.exportSettings = clientResource.authorization().exportSettings()
                }

                val isServiceAccountEnabled = clientRepresentation.isServiceAccountsEnabled ?: false
                if (isServiceAccountEnabled) {
                    clientResource.serviceAccountUser?.let { serviceAccountUser ->
                        clientExportDto.serviceAccountUser =
                            getServiceAccount(serviceAccountUser, realmResource)
                    }
                }
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
    fun getServiceAccount(
        userRepresentation: UserRepresentation,
        realmResource: RealmResource
    ): UserRepresentation? {

        try {
            realmResource.users().get(userRepresentation.id)?.let { userResource ->

                val clientRoles = mutableMapOf<String, List<String>>()
                userResource.roles().all.clientMappings.map { (key, value) ->
                    clientRoles[key] = value.mappings.map { it.name }.toList()
                }
                val realmRoles = userResource.roles().all.realmMappings
                    .filter { !it.description.startsWith("$") }.map { it.name }.toList()
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
        return null
    }

}

