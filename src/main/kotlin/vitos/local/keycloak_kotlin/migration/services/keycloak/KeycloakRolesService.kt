package vitos.local.keycloak_kotlin.migration.services.keycloak

import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.logging.Log

/**
 * Класс управления realm и client ролями в Keycloak
 * @author Belotserkovskii Vitaly
 */
@Service
class KeycloakRolesService {

    companion object: Log()


    /**
     * Выполняет создание роли области сервисов
     *
     * @param roleRepresentation сущность новой роли
     * @param realmResource ресурс управления областью сервисов
     * @return сущность созданной роли или null если роль существует
     */
    fun createRealmRole(

        roleRepresentation: RoleRepresentation,
        realmResource: RealmResource

    ): RoleRepresentation? {

        val roleName = roleRepresentation.name
        try {
            realmResource.roles().create(roleRepresentation)
            realmResource.roles().get(roleName)?.toRepresentation()?.let {
                return it
            }
        } catch (ex: Exception) {
            logger.errorM("Creating of Realm role $roleName failed ${ex.message}", ex)
        }
        return null
    }


    /**
     * Заменяет сущность существующей роли на новую, переданную параметром
     *
     * @param newRepresentation новая сущность роли
     * @param existRepresentation прежняя сущность роли
     * @param realmResource ресурс управления областью сервисов
     * @return сущность обновленной роли или null если failed
     */
    fun updateRealmRoles(

        newRepresentation: RoleRepresentation,
        existRepresentation: RoleRepresentation,
        realmResource: RealmResource

    ): RoleRepresentation? {

        val roleName = existRepresentation.name
        try {
            newRepresentation.id = existRepresentation.id
            realmResource.roles().get(roleName)?.let { roleResource ->
                roleResource.update(newRepresentation)
                return roleResource.toRepresentation()
            }
        } catch (ex: Exception) {
            logger.errorM("Updating of Realm role $roleName failed ${ex.message}", ex)
        }
        return null
    }


    /**
     * Выполняет создание client роли в области сервисов
     *
     * @param roleRepresentation сущность новой роли
     * @param clientResource ресурс управления сервисом
     * @return сущность созданной роли или null если роль существует
     */
    fun createClientRole(
        roleRepresentation: RoleRepresentation,
        clientResource: ClientResource,
    ): RoleRepresentation? {

        val roleName = roleRepresentation.name
        try {
            clientResource.roles().create(roleRepresentation)
            clientResource.roles().get(roleName)?.toRepresentation()?.let {
                return it
            }
        } catch (ex: Exception) {
            logger.errorM("Creating of RoleRepresentation $roleName failed by = ${ex.message}", ex)
        }
        return null
    }
}