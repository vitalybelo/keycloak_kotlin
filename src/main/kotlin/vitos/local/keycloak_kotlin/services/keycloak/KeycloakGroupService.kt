package vitos.local.keycloak_kotlin.services.keycloak

import org.keycloak.admin.client.resource.GroupResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.CREATE_ROLE_DESC
import vitos.local.keycloak_kotlin.logging.Log


/**
 * Класс управления ролями области сервисов в задачах миграции
 * @author Vitaly Belotserkovskii
 */
@Service
class KeycloakGroupService(

    private val keycloakRolesService: KeycloakRolesService,
    private val keycloakClientService: KeycloakClientService
) {

    companion object : Log()


    /**
     * Выполняет присвоение client ролей для созданной группы. По карте client ролей, переданных
     * в импорте для группы, метод выполняет поиск нужного клиента и вычитывает список его ролей.
     * При совпадении нужной для присвоения и существующей в client ролей - выполняется присваивание,
     * или создание роли для сервиса с последующим присваиванием группе. Если сервис не найден, метод
     * создает такой сервис и последовательно назначает ему роли, которые нужно присвоить группе
     *
     * @param groupRepresentation сущность создаваемой корневой группы
     * @param groupResource ресурс управления группой
     * @param realmResource ресурс управления realm
     */
    fun assignClientRolesToGroup(

        groupRepresentation: GroupRepresentation,
        groupResource: GroupResource,
        realmResource: RealmResource,

    ) {
        val groupName = groupRepresentation.name
        val clientRoles: Map<String, List<String>?>? = groupRepresentation.clientRoles
        if (clientRoles.isNullOrEmpty()) {
            logger.infoM("Group $groupName has no one client role to assign for it :: return")
            return
        }
        try {
            clientRoles.forEach { role ->

                val clientId = role.key
                keycloakClientService.findOrCreateClient(clientId, realmResource)?.let { client ->
                    logger.infoM("Found client $clientId :: going to perform add roles for it")

                    val clientUUID = client.id
                    val rolesToAdd= mutableListOf<RoleRepresentation>()
                    realmResource.clients().get(clientUUID)?.let { clientResource ->

                        val found =
                            clientResource.roles().list()?.associateBy { it.name } ?: emptyMap()

                        role.value?.forEach { roleName ->
                            if (found.containsKey(roleName)) {
                                logger.infoM("Found client role: $roleName for adding to group: $groupName")
                                rolesToAdd.add(found[roleName]!!)
                            } else {
                                keycloakClientService.createClientRoles(roleName, clientResource)?.let {
                                    logger.infoM("Created client role: $roleName for adding to group: $groupName")
                                    rolesToAdd.add(it)
                                }
                            }
                        }
                        if (rolesToAdd.isNotEmpty()) {
                            groupResource.roles().clientLevel(clientUUID).add(rolesToAdd)
                        }
                    }
                } ?: logger.infoM("Client $clientId is fatal unreachable :: refuse to add client roles for it")
            }
        } catch (ex: Exception) {
            logger.errorM("Error assigning client roles: ${ex.message}, ${ex.cause}", ex)
        }
    }


    /**
     * Выполняет поиск существующих realm ролей, а затем присваивает требуемые группе найденные роли.
     * Если по каким-то причинам нужной realm роли нет, метод добавляет ее в рабочую область, после
     * чего присваивает группе.
     *
     * @param groupRepresentation сущность группы со списков ролей, которые нужно присвоить
     * @param groupResource ресурс управления группой
     * @param realmResource ресурс управления realm
     */
    fun assignRealmRolesToGroup(

        groupRepresentation: GroupRepresentation,
        groupResource: GroupResource,
        realmResource: RealmResource,

    ) {
        val groupName = groupRepresentation.name
        val realmRoles = groupRepresentation.realmRoles
        if (realmRoles.isNullOrEmpty()) {
            logger.infoM("Group $groupName has no one realm role to assign for it :: return")
            return
        }
        try {
            val foundRoles = realmResource.roles()
                .list(0, Integer.MAX_VALUE, false)
                ?.associateBy { it.name } ?: emptyMap()

            val rolesToAdd = mutableListOf<RoleRepresentation>()
            realmRoles.forEach { roleName ->
                if (foundRoles.isNotEmpty() && foundRoles.containsKey(roleName)) {
                    // роль существует в области - поэтому просто добавляем
                    logger.infoM("Found realm role: $roleName for adding to group: $groupName")
                    rolesToAdd.add(foundRoles[roleName]!!)
                } else {
                    // нужной роли нет в области - создаем новую роль для присваивания
                    val roleRepresentation = RoleRepresentation().apply {
                        name = roleName
                        description = CREATE_ROLE_DESC
                    }
                    keycloakRolesService.createRealmRole(roleRepresentation, realmResource)?.let {
                        logger.infoM("Created realm role: $roleName for adding to group: $groupName")
                        rolesToAdd.add(it)
                    }
                }
            }
            if (rolesToAdd.isNotEmpty()) {
                groupResource.roles().realmLevel().add(rolesToAdd)
                return
            }
        } catch (ex: Exception) {
            logger.errorM("Error assigning roles for group: ${ex.message}", ex)
        }
    }


    /**
     * Выполняет чтение списка корневых групп области сервисов. Для каждой найденной корневой группы,
     * вызывается рекурсивный метод чтения всех вложенных подгрупп ниже по дереву.
     *
     * @param realmResource ресурс управления областью сервисов
     * @return полный список групп, с включенными подгруппами всех уровней
     */
    fun collectGroups(
        realmResource: RealmResource
    ): List<GroupRepresentation> {
        // read list of root groups and collect subgroups for each below
        try {
            val groupList: List<GroupRepresentation> = realmResource
                .groups().groups("", 0, Integer.MAX_VALUE, false) ?: emptyList()

            groupList.forEach { group ->
                if (group.subGroups.isEmpty()) {
                    collectSubGroups(group, realmResource)
                }
            }
            return groupList

        } catch (ex: Exception) {
            logger.errorM("Error collecting realms groups: ${ex.message}", ex)
        }
        return emptyList()
    }


    /**
     * Выполняет рекурсивный поиск подгрупп для заданной параметром родительской группы.
     * Изменения вносятся в родительскую группу, путем добавления найденных подгрупп к списку
     * subGroups родительской группы
     *
     * @param groupRepresentation сущность корневой группы, для которой сканируется список дочерних
     * @param realmResource ресурс управления областью сервисов
     */
    fun collectSubGroups(
        groupRepresentation: GroupRepresentation,
        realmResource: RealmResource
    ) {

        val groupName = groupRepresentation.name
        try {
            if (groupRepresentation.subGroupCount > 0) {
                val resource = realmResource.groups().group(groupRepresentation.id)
                val subGroups =
                    resource.getSubGroups(0, Integer.MAX_VALUE, false, true)
                subGroups.forEach { subGroup ->
                    groupRepresentation.subGroups.add(subGroup)
                    logger.debugM("Sub group: ${subGroup.name} added to parent group $groupName")
                    collectSubGroups(subGroup, realmResource)
                }
            }
            return
        } catch (ex: Exception) {
            logger.errorM("Error collecting subgroups for $groupName :: ${ex.message}", ex)
        }
    }


    fun getGroupResource(
        groupRepresentation: GroupRepresentation,
        realmResource: RealmResource
    ): GroupResource? {

        try {
            val groupResource = realmResource.groups().group(groupRepresentation.id)
            val representation = groupResource.toRepresentation()
            if (representation != null) {
                return groupResource
            }
        } catch (ex: Exception) {
            logger.errorM("Impossible to get group \"${groupRepresentation.name}\" resource", ex)
        }
        return null
    }
}

