package vitos.local.keycloak_kotlin.services

import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import vitos.local.keycloak_kotlin.authorization.AccessTokenService
import vitos.local.keycloak_kotlin.configs.KeycloakTokenService
import vitos.local.keycloak_kotlin.models.OpenIdConfiguration
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.databind.ObjectMapper
import vitos.local.keycloak_kotlin.models.BruteForceUserRepresentation

@Suppress("unused")
@Service
class KeycloakService(

    @Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private val issuerURL: String? = null,
    @Value("\${keycloak.server.url}")
    private val keycloakServerURL: String? = null,
    @Value("\${keycloak.realm}")
    private val keycloakRealm: String? = null,
    private val realmResource: RealmResource,
    private val restTemplate: RestTemplate,
    private val accessTokenService: AccessTokenService,
    private val keycloakTokenService: KeycloakTokenService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

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
     * @param username username пользователя
     * @param password новый пароль пользователя
     * @param headers карта заголовков http запроса
     * @return строку сообщения о выполнении и статус выполнения
     */
    fun changeUserPassword(
        username: String?,
        password: String?,
        headers: Map<String, String>,
    ): ResponseEntity<Any> {

        fun notFoundResponse(message: String): ResponseEntity<Any> {
            return ResponseEntity(message, HttpStatus.NOT_FOUND)
        }

        val userName = username
            ?: accessTokenService.assign(headers)?.login
            ?: return notFoundResponse("Parameter username missed")

        log.info("Changing password procedure for: $userName is starting...")
        try {
            // Ищем пользователя и получаем ресурс администрирования пользователя и области сервисов
            val user = realmResource.users().searchByUsername(userName, true).firstOrNull()
                ?: return notFoundResponse("User not found")

            val usersResource = realmResource.users().get(user.id)
                ?: return notFoundResponse("Impossible to receive user resource")

            val realm: RealmRepresentation = realmResource.toRepresentation()
                ?: return notFoundResponse("Impossible to receive realm representation")

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

            return ResponseEntity("Password changed successfully for user: $userName", HttpStatus.OK)

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
     * @param user сущность пользователя keycloak
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
                val result: OpenIdConfiguration = objectMapper.readValue(value)
                return ResponseEntity(result, HttpStatus.OK)
            }
        } catch (e: Exception) {
            log.info(">>>> Ошибка чтения конфигурации области сервисов >>>> {}", e.message)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя по этому идентификатору
     *
     * @param authentication класс аутентификации для получения токена
     * @return сущность пользователя keycloak - UserRepresentation
     */
    fun getUserRepresentation(authentication: Authentication): ResponseEntity<Any> {

        val accessToken = accessTokenService.assign(authentication)
        if (accessToken != null) {
            realmResource.users().get(accessToken.userId)?.toRepresentation()?.let {
                return ResponseEntity(it, HttpStatus.OK)
            } ?: return ResponseEntity("Пользователь не найден", HttpStatus.NOT_FOUND)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя с расширенной информацией по brute-force.
     * @return сущность пользователя с информацией по brute-force BruteForceUserRepresentation
     */
    fun getExtendedUserRepresentation(): ResponseEntity<Any> {

        val userId = accessTokenService.assign()?.userId
        val bruteForceUserList: List<BruteForceUserRepresentation>? = getBruteForceUserList()
        if (userId != null && bruteForceUserList != null) {

            bruteForceUserList.stream()
                .filter { user -> user.id.equals(userId) }.findFirst().orElse(null)?.let {
                    return ResponseEntity(it, HttpStatus.OK)
                }
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет чтение списка учетных данных пользователя с расширенной информацией по brute-force.
     * @return список сущностей пользователя с информацией по brute-force
     */
    fun getExtendedUserRepresentationList(): ResponseEntity<Any> {

        getBruteForceUserList()?.let {
            return ResponseEntity(it, HttpStatus.OK)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет запрос в keycloak rest api ext-ui списка пользователей я расширенно информацией
     * по временным блокировкам brute-force, с статусом блокировок
     *
     * @param offset смещение пагинации (дефолтное значение = 0)
     * @param limit ограничение пагинации (дефолтное значение = 1000000)
     * @return список сущностей пользователей Keycloak
     */
    private fun getBruteForceUserList(
        offset: Int = 0,
        limit: Int = 1_000_000
    ): List<BruteForceUserRepresentation>? {

        try {
            val headers: HttpHeaders = keycloakTokenService.getOauth2Headers()
            val requestEntity = HttpEntity<Void>(headers)

            val res = restTemplate.exchange(
                "$keycloakServerURL/admin/realms/$keycloakRealm/ui-ext/brute-force-user?first=$offset&max=$limit",
                HttpMethod.GET,
                requestEntity,
                Any::class.java,
                object : ParameterizedTypeReference<Any?>() {
                })

            if (res.statusCode == HttpStatus.OK) {

                val value = objectMapper.writeValueAsString(res.body)
                val bruteForceUserList: List<BruteForceUserRepresentation> = objectMapper.readValue(value)
                return bruteForceUserList
            }
        } catch (e: java.lang.Exception) {
            log.error(">>>> Request to Keycloak UI-EXT failed >>>> {}", e.message)
        }
        return null
    }

}