package vitos.local.keycloak_kotlin.services.keycloak

import org.keycloak.admin.client.resource.ClientResource
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.CREATE_CLIENT_DESC
import vitos.local.keycloak_kotlin.constants.Constants.Companion.CREATE_ROLE_DESC
import vitos.local.keycloak_kotlin.logging.Log


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
        realmResource: RealmResource,
    ): ClientRepresentation? {

        // пробуем найти клиенты в области, и если находим, возвращаем его сущность
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

}

