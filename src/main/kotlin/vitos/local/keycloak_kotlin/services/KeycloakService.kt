package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import vitos.local.keycloak_kotlin.models.OpenIdConfiguration


@Service
class KeycloakService(

    @Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private val issuerURL: String? = null,
    private val realmResource: RealmResource,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper

    ) {

    companion object {
        private val log = LoggerFactory.getLogger(KeycloakService::class.java)
        const val FATAL_ERROR = "Непредвиденная ошибка"
    }


    /**
     * Выполняет замену пароля для заданного в запросе пользователя. Метод учитывает наличие
     * настроек политик безопасности для сброса паролей, поэтому вначале сохраняет действующие
     * политики, затем обнуляет политики для области (realm), выполняет сброс пароля на любой
     * заданный и затем восстанавливает дефолтные для области
     *
     * @param userName - username пользователя
     * @param password - новый пароль пользователя
     * @return строку сообщения о выполнении и статус выполнения
     */
    fun changeUserPassword(
        userName: String,
        password: String
    ): ResponseEntity<String> {

        log.info("Changing password procedure is starting...")
        try {
            // Ищем пользователя и получаем ресурс администрирования
            val user = realmResource.users().searchByUsername(userName, true).firstOrNull()
                ?: return ResponseEntity("User not found", HttpStatus.NOT_FOUND)

            val usersResource = realmResource.users().get(user.id)
                ?: return ResponseEntity("Impossible to receive user resource", HttpStatus.NOT_FOUND)

            val realm: RealmRepresentation = realmResource.toRepresentation()
                ?: return ResponseEntity("Impossible to receive realm representation", HttpStatus.NOT_FOUND)

            // сохраняем существующие политики установленные для пароля и сбрасываем их временно
            val passwordPolicies: String? = realm.passwordPolicy
            realm.passwordPolicy = ""
            realmResource.update(realm)
            log.info("Realm password policies reset ...")

            // создаем новую сущность для пароля пользователя типа PASSWORD и выполняем сброс пароля
            val credential = CredentialRepresentation()
            credential.type = CredentialRepresentation.PASSWORD
            credential.value = password
            credential.isTemporary = false

            usersResource.resetPassword(credential)

            // восстанавливаем политики для паролей до дефолтных для области сервисов
            realm.passwordPolicy = passwordPolicies
            realmResource.update(realm)
            log.info("Realm password policies restored ...")

            return ResponseEntity("Password changed successfully", HttpStatus.OK)

        } catch (e: Exception) {
            log.error("Error during changing password\n {}", e.localizedMessage)
        }
        return ResponseEntity("Error during changing password", HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет создание пользователя в keycloak.
     * Вначале метод пытается создать нового пользователя. Если не заканчивается кодом 201 = создано,
     * выполняется поиск пользователя в keycloak и при успешном выполнении - возвращается сущность
     *
     * @param user - сущность пользователя keycloak
     * @return сущность созданного или найденного пользователя, или ошибка
     */
    fun createKeycloakUser(user: UserRepresentation): ResponseEntity<Any> {

        val userName = user.username
        try {
            realmResource.users().create(user).use { response ->
                if (response.status == 201) {
                    val userId = CreatedResponseUtil.getCreatedId(response)
                    if (!userId.isNullOrBlank()) {
                        log.info(">>>> User {} created :: {}", userName, userId)
                        realmResource.users()?.get(userId)?.toRepresentation()?.also {
                            return ResponseEntity(it, HttpStatus.CREATED)
                        }
                    }
                }
                log.info(">>>> Create failed, search existing by username :: {}", userName)
                realmResource.users().searchByUsername(userName, true).firstOrNull()?.also {
                    return ResponseEntity(it, HttpStatus.OK)
                }
            }
        } catch (e: Exception) {
            log.info(">>>> Fatal error creating user :: {}", userName)
        }
        return ResponseEntity("Fatal error creating user in keycloak", HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Возвращает информацию о конечных точках сервера авторизации Keycloak
     * @return json ответа конечной точки /.well-known/openid-configuration
     */
    fun getWellKnownEndPoints(): ResponseEntity<Any> {

        val configUrl = "$issuerURL/.well-known/openid-configuration"
        try {
            val response = restTemplate.getForEntity(configUrl, Any::class.java)
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                val value = objectMapper.writeValueAsString(response.body)
                val result = objectMapper.readValue(value, OpenIdConfiguration::class.java)
                return ResponseEntity(result, HttpStatus.OK)
            }
        } catch (e: Exception) {
            log.info(">>>> Ошибка чтения конфигурации области сервисов >>>> {}", e.message)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.OK)
    }


}