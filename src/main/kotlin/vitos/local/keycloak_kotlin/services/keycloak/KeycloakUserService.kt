package vitos.local.keycloak_kotlin.services.keycloak

import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.CREATE_ROLE_DESC
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.models.ClientExportDto


/**
 * Сервисный слой для обеспечения методов импорта пользователей
 * @author Vitaly Belotserkovskii
 */
@Service
class KeycloakUserService(

    private val keycloakClientService: KeycloakClientService,
    private val keycloakRolesService: KeycloakRolesService,
    private val keycloakGroupService: KeycloakGroupService
) {

    companion object: Log()


    /**
     * Выполняет обновление учетной записи системного пользователя для Client
     *
     * @param clientExportDto экспортная сущность нового сервиса
     * @param clientResource ресурс управления сервисом Client
     * @param realmResource ресурс управления областью сервисов
     */
    fun updateServiceAccountUser(

        clientExportDto: ClientExportDto,
        clientResource: ClientResource,
        realmResource: RealmResource
    ) {
        clientExportDto.serviceAccountUser?.let { importServiceUserAccount ->
            clientResource.serviceAccountUser?.let { foundServiceUserAccount ->

                val userId = foundServiceUserAccount.id
                val userName = foundServiceUserAccount.username
                try {
                    val userResource = realmResource.users().get(userId)
                    // переопределяем атрибуты системного пользователя
                    foundServiceUserAccount.attributes = importServiceUserAccount.attributes
                    userResource?.update(foundServiceUserAccount)
                    // назначаем realm роли системному пользователю
                    assignRealmRolesToUser(
                        importServiceUserAccount,
                        userResource,
                        realmResource
                    )
                    assignClientRolesToUser(
                        importServiceUserAccount,
                        userResource,
                        realmResource
                    )
                    assignGroupsToUser(
                        importServiceUserAccount,
                        userResource,
                        realmResource
                    )
                    logger.infoM("Service account user $userName successfully updated")

                } catch (ex: Exception) {
                    logger.errorM("failed to update serviceAccountUser = $userName",ex)
                }
            }
        }
    }


    /**
     * Назначение пользователю client ролей из импортной сущности в существующую
     *
     * @param importUserAccount импортная сущность системного пользователя
     * @param userResource ресурс управления существующим системным пользователем
     * @param realmResource ресурс управления областью
     */
    fun assignClientRolesToUser(

        importUserAccount: UserRepresentation,
        userResource: UserResource,
        realmResource: RealmResource
    ) {
        val userName = importUserAccount.username
        val userClientRoles = importUserAccount.clientRoles
        if (userClientRoles.isNullOrEmpty()) {
            logger.infoM("User $userName has no one client role to assign for it :: return")
            return
        }
        deleteClientRolesFromUser(userResource, realmResource, userName)

        userClientRoles.forEach { importClientRole ->
            try {
                val clientId = importClientRole.key
                keycloakClientService.findOrCreateClient(clientId, realmResource)?.let { client ->
                    logger.infoM("Found or created client $clientId :: going to perform add roles for it")

                    val clientUUID = client.id
                    val clientResource = realmResource.clients().get(clientUUID)
                    val foundClientRoles =
                        clientResource.roles().list()?.associateBy { it.name } ?: emptyMap()

                    val importUserClientRoles = importClientRole.value
                    val rolesToAdd = mutableListOf<RoleRepresentation>()

                    // присваиваем пользователю новый список ролей для текущего client
                    importUserClientRoles.forEach { roleName ->

                        if (foundClientRoles.containsKey(roleName)) {
                            rolesToAdd.add(foundClientRoles[roleName]!!)
                            logger.infoM("Found client role $roleName that add to user $userName")
                        } else {
                            keycloakRolesService.createClientRole(RoleRepresentation().apply {
                                this.name = roleName
                                this.description = CREATE_ROLE_DESC
                            }, clientResource)?.let {
                                rolesToAdd.add(it)
                                logger.infoM("Created client role $roleName that add to user $userName")
                            }
                        }
                    }
                    // добавляем роли для текущего client
                    if (rolesToAdd.isNotEmpty()) {
                        userResource.roles().clientLevel(clientUUID).add(rolesToAdd)
                        logger.infoM("Roles list = $rolesToAdd successfully added to user $userName")
                    }
                } ?: logger.infoM("Client $clientId is fatal unreachable :: refuse to add client roles for it")

            } catch (ex: Exception) {
                logger.error("Error while assigning client role to user $userName :: ${ex.message}", ex)
            }
        }
    }


    /**
     * Удаляет все clients роли у пользователя, безвозвратно
     * @param userResource ресурс управления существующим системным пользователем
     * @param realmResource ресурс управления областью
     * @param userName имя пользователя
     */
    fun deleteClientRolesFromUser(
        userResource: UserResource,
        realmResource: RealmResource,
        userName: String
    ) {
        try {
            val clientMapping = userResource.roles().all?.clientMappings
            if (!clientMapping.isNullOrEmpty()) {
                clientMapping.forEach { clientRole ->
                    val clientId = clientRole.key
                    val roleList = clientRole.value?.mappings
                    val clientRepresentation = realmResource.clients().findByClientId(clientId).firstOrNull()
                    if (clientRepresentation != null) {
                        userResource.roles().clientLevel(clientRepresentation.id).remove(roleList)
                    }
                }
                logger.infoM("All clients roles for user $userName were successfully removed")
            }
        } catch (ex: Exception) {
            logger.error("Error while deleting client role from user $userName", ex)
        }

    }


    /**
     * Назначение пользователю realm ролей из импортной сущности в существующую
     *
     * @param importUserAccount импортная сущность системного пользователя
     * @param userResource ресурс управления существующим системным пользователем
     * @param realmResource ресурс управления областью
     */
    fun assignRealmRolesToUser(

        importUserAccount: UserRepresentation,
        userResource: UserResource,
        realmResource: RealmResource
    ) {
        val userName = importUserAccount.username
        logger.infoM("Start to add realm roles for User = $userName")

        // получаем карту всех realm ролей в области сервисов
        val foundRealmRoles = realmResource.roles()
            .list(0, Integer.MAX_VALUE, false)
            ?.associateBy { it.name } ?: emptyMap()

        // удаляем все роли пользователя (кроме default) и потом назначаем заново
        val foundUserRoles: List<RoleRepresentation>? =
            userResource.roles().all.realmMappings?.filter {
                !it.description.startsWith("$")
            }
        if (!foundUserRoles.isNullOrEmpty()) {
            userResource.roles().realmLevel().remove(foundUserRoles)
        }

        val rolesAddList = mutableListOf<RoleRepresentation>()
        importUserAccount.realmRoles?.forEach { roleName ->

            if (foundRealmRoles.isNotEmpty() && foundRealmRoles.containsKey(roleName)) {
                // роль существует в realm - просто добавляем существующую пользователю
                rolesAddList.add(foundRealmRoles[roleName]!!)
                logger.infoM("Realm role = $roleName found for adding to user = $userName")
            } else {
                // роль не существует в realm - создаем новую и добавляем пользователю
                keycloakRolesService.createRealmRole(RoleRepresentation().apply {
                    name = roleName
                    description = CREATE_ROLE_DESC
                }, realmResource)?.let { rolesAddList.add(it) }
                logger.infoM("Realm role = $roleName created for adding to user = $userName")
            }
        }
        // назначаем роли пользователю
        if (rolesAddList.isNotEmpty()) {
            userResource.roles().realmLevel().add(rolesAddList)
            logger.infoM("Realm role list = $rolesAddList successfully added to user $userName")
        }
    }


    /**
     * Выполняет обновление учетной записи системного пользователя для Client
     *
     * @param importUserAccount импортная сущность системного пользователя
     * @param userResource ресурс управления пользователем
     * @param realmResource ресурс управления областью сервисов
     */
    fun assignGroupsToUser(
        importUserAccount: UserRepresentation,
        userResource: UserResource,
        realmResource: RealmResource
    ) {
        val importedUserGroups: List<String>? = importUserAccount.groups
        val foundUserGroup = userResource.groups() ?: emptyList()

        if (!importedUserGroups.isNullOrEmpty()) {

            // назначаем пользователю группы (если они существуют в realm)
            importedUserGroups.forEach { groupPath ->
                keycloakGroupService.findGroupByPath(groupPath, realmResource)?.let { groupRepresentation ->
                    val groupId = groupRepresentation.id
                    if (foundUserGroup.stream().noneMatch { it.path.equals(groupPath) }) {
                        userResource.joinGroup(groupId)
                    }
                }
            }
            // убираем группы, которые больше не нужны
            foundUserGroup.forEach { groupRepresentation ->
                val path = groupRepresentation.path
                if (importedUserGroups.stream().noneMatch { it.equals(path) }) {
                    userResource.leaveGroup(groupRepresentation.id)
                }
            }

        } else {
            foundUserGroup.forEach { groupRepresentation ->
                userResource.leaveGroup(groupRepresentation.id)
            }
        }
    }

}

