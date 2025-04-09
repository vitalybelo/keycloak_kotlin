package vitos.local.keycloak_kotlin.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import org.keycloak.admin.client.resource.RealmResource
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*


/**
 * Класс для извлечения учетных данных и ролей пользователя из "Bearer" или "JSESSIONID" токенов доступа.
 * @author Belotserkovskii Vitalii, 24.03.2025
 */
@Service
@Suppress("unused")
class AccessTokenService(

    private val realmResource: RealmResource,
    private val objectMapper: ObjectMapper
) {

    private var accessToken: AccessToken? = null
    private val logger = LoggerFactory.getLogger(AccessTokenService::class.java)

    companion object {
        const val TOKEN_PREFIX: String = "Bearer "
        const val AUTHORIZATION_HEADER: String = "Authorization"
    }


    /**
     * Проверяет доступность класса аутентификации spring security для чтения данных пользователя из токена доступа
     * @return true если security context доступен
     */
    private fun isSpringContext(): Boolean {
        return Optional.ofNullable(SecurityContextHolder.getContext())
            .map { obj: SecurityContext -> obj.authentication }.isPresent
    }


    /**
     * Читает из контекста безопасности spring security класс аутентификации пользователя, если он там есть
     * @return инициализированный data класс AccessToken или null
     */
    fun assign(): AccessToken? {

        SecurityContextHolder.getContext()?.authentication?.let { return assign(it) } ?: return null
    }


    /**
     * В зависимости от источника запроса и типа токена (JWT или JSESSIONID), инициализирует авторизационный
     * класс AccessToken для чтения данных пользователя из токена аутентификации.
     *
     * @param authentication класс аутентификации spring boot security
     * @return инициализированный data класс AccessToken или null
     */
    fun assign(authentication: Authentication?): AccessToken? {

        authentication?.let {

            val principal = authentication.principal

            // Проверяем аутентификацию, выполненную по типу > BEARER
            if (principal is Jwt) {
                parseAccessToken(principal.tokenValue)?.let { return it }
            }

            // Проверяем аутентификацию, выполненную по типу > JSESSIONID
            if (principal is DefaultOidcUser) {
                parseAccessToken(principal.idToken.tokenValue)?.let { return it }
            }
        }
        return null
    }


    /**
     * Извлекает из заголовка http запроса токен доступа, и инициализирует с помощью него класс AccessToken
     *
     * @param headers - карта заголовков http запроса
     * @return инициализированный data класс AccessToken или null
     */
    fun assign(headers: Map<String, String>): AccessToken? {

        val key = getIgnoreCaseHeaderAuthorization(headers)
        if (key != null) {
            headers[key]?.replace(TOKEN_PREFIX, "")?.let { return parseAccessToken(it) }
        }
        return null
    }


    /**
     * Выполняет поиск среди ключей карты http заголовков - заголовка Authorization без учета регистра
     *
     * @param headers - карта http заголовков
     * @return строку валидного ключа для извлечения или null
     */
    private fun getIgnoreCaseHeaderAuthorization(headers: Map<String, String>): String? {
        return headers.keys.stream()
            .filter { key -> key.equals(AUTHORIZATION_HEADER, true) }
            .findFirst().orElse(null)
    }


    /**
     * Метод извлекает экземпляр класса авторизации AccessToken из payload токена доступа keycloak.

     * @param tokenString строка с токеном доступа keycloak, без префикса Bearer
     * @return экземпляр класса AccessToken, или null в случае ошибки
     */
    private fun parseAccessToken(tokenString: String?): AccessToken? {

        accessToken = null
        if (!tokenString.isNullOrEmpty()) {

            val decoder = Base64.getUrlDecoder()
            val chunks = tokenString.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            if (chunks.size > 1) {
                val payload = String(decoder.decode(chunks[1]))
                try {
                    accessToken = objectMapper.readValue(payload, AccessToken::class.java)

                } catch (e: Exception) {
                    logger.info(">>> Ошибка парсинга токена доступа: {}", e.message)
                }
            }
        }
        return accessToken
    }


    /**
     * @return извлекает и возвращает из карты ролей области все значения
     */
    fun streamRealmRoles(): List<String> {

        val roles: MutableList<String> = ArrayList()
        if (accessToken != null || assign() != null) {
            try {
                accessToken?.realmRolesMap?.values?.forEach { value ->
                    roles.addAll(value.toList())
                }
            } catch (ignored: Exception) {
            }
        }
        return roles
    }


    /**
     * @return извлекает и возвращает из карты ролей сервисов все значения
     */
    fun streamClientRoles(): List<String> {

        val roles: MutableList<String> = ArrayList()
        if (accessToken != null || assign() != null) {
            try {
                accessToken?.clientRolesMap?.values?.forEach { map ->
                    map.values.forEach { value -> roles.addAll(value.toList()) }
                }
            } catch (ignored: Exception) {
            }
        }
        return roles
    }

}