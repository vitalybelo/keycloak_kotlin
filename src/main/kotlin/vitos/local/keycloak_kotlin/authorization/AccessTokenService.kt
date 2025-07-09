package vitos.local.keycloak_kotlin.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.*


/**
 * Класс для извлечения учетных данных и ролей пользователя из "Bearer" или "JSESSIONID" токенов доступа.
 * @author Belotserkovskii Vitalii, 24.03.2025
 */
@Service
@Suppress("unused")
class AccessTokenService(
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
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
    private fun isSpringContext() =
        Optional
            .ofNullable(SecurityContextHolder.getContext())
            .map { obj: SecurityContext -> obj.authentication }.isPresent


    /**
     * Читает из контекста безопасности spring security класс аутентификации пользователя, если он там есть
     * @return инициализированный data класс AccessToken или null
     */
    fun assign(): AccessToken? {

        getHttpServletRequest()?.let { request ->
            assign(request)?.let { return it }
        }
        SecurityContextHolder.getContext()?.authentication?.let { authentication ->
            assign(authentication)?.let { return it }
        }
        return null
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
     * Метод извлекает принципал из контекста безопасности spring security. В зависимости от источника
     * запроса и типа токена (JWT или JSESSIONID) извлекается карта с утверждениями токена
     *
     * @return карта с утверждениями токена или пустая
    */
    fun getClaims(): Map<String, Any> {
        SecurityContextHolder.getContext()?.authentication?.principal?.let { principal ->
            if (principal is DefaultOidcUser) {
                return principal.claims
            }
            if (principal is Jwt) {
                return principal.claims
            }
        }
        return emptyMap()
    }


    /**
     * Извлекает из заголовка http запроса токен доступа, и инициализирует с помощью него класс AccessToken
     *
     * @param headers - карта заголовков http запроса
     * @return инициализированный data класс AccessToken или null
     */
    fun assign(headers: Map<String, String>): AccessToken? {

        getIgnoreCaseAuthorizationHeader(headers)?.let {
            return parseAccessToken(it)
        }
        return null
    }


    /**
     * Извлекает из заголовка http запроса токен доступа, и инициализирует с помощью него класс AccessToken
     *
     * @param request - сервлет http запроса
     * @return инициализированный data класс AccessToken или null
     */
    fun assign(request: HttpServletRequest): AccessToken? {

        getIgnoreCaseAuthorizationHeader(request)?.let {
            return parseAccessToken(it) }
        return null
    }

    /**
     * Выполняет поиск среди ключей карты http заголовков - заголовка Authorization без учета регистра.
     * Если находит, извлекает значение заголовка, удаляет префикс и возвращает токен доступа
     *
     * @param headers - карта http заголовков
     * @return jwt токен доступа или null
     */
    private fun getIgnoreCaseAuthorizationHeader(headers: Map<String, String>): String? {
        headers.keys.stream()
            .filter { key -> key.equals(AUTHORIZATION_HEADER, true) }
            .findFirst().orElse(null)?.let { key ->
                headers[key]?.removePrefix(TOKEN_PREFIX)?.let {
                    return it
                }
            }
        return null
    }

    /**
     * Выполняет поиск среди ключей карты http заголовков - заголовка Authorization без учета регистра.
     * Если находит, извлекает значение заголовка, удаляет префикс и возвращает токен доступа
     *
     * @param request - сервлет http запроса
     * @return jwt токен доступа или null
     */
    private fun getIgnoreCaseAuthorizationHeader(request: HttpServletRequest): String? {
        request.headerNames.asSequence().toList().stream()
            .filter { key -> key.equals(AUTHORIZATION_HEADER, true) }
            .findFirst().orElse(null)?.let { key ->
                request.getHeader(key)?.removePrefix(TOKEN_PREFIX)?.let {
                    return it
                }
            }
        return null
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
                    accessToken = objectMapper.readValue(payload)

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
        try {
            return (accessToken ?: assign())?.realmRolesMap?.values?.flatMap { it } ?: emptyList()
        } catch (ex: Exception) {
            logger.error("Crashed in streamRealmRoles() ${ex.message}", ex)
        }
        return emptyList()
    }


    /**
     * @return извлекает и возвращает из карты ролей сервисов все значения
     */
    fun streamClientRoles(): List<String> {
        try {
            return (accessToken ?: assign())?.clientRolesMap?.values?.flatMap { it.values.flatten() } ?: emptyList()
        } catch (ex: Exception) {
            logger.error("Crashed in streamClientRoles() ${ex.message}", ex)
        }
        return emptyList()
    }


    /**
     * Извлекает из контекста сервлета экземпляр класса HttpServletRequest
     * @return экземпляр класса HttpServletRequest или null
     */
    private fun getHttpServletRequest(): HttpServletRequest? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        return requestAttributes?.request
    }
}