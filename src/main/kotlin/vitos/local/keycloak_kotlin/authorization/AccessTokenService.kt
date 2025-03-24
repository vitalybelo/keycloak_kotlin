package vitos.local.keycloak_kotlin.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.lang3.StringUtils
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
    private val objectMapper: ObjectMapper,
) {

    private var accessToken: AccessToken? = null
    private val logger = LoggerFactory.getLogger(AccessTokenService::class.java)

    companion object {
        const val TOKEN_PREFIX: String = "Bearer "
    }


    /**
     * Проверяет доступность класса аутентификации spring security для чтения данных пользователя из токена доступа
     * @return true если security context доступен
     */
    private fun isSpringContext(): Boolean {
        return Optional.ofNullable(SecurityContextHolder.getContext())
                        .map { obj: SecurityContext -> obj.authentication }
                        .isPresent
    }


    /**
     * В зависимости от состава контекста безопасности spring boot security, инициализирует авторизационный
     * класс AccessToken для чтения данных пользователя из токена аутентификации.
     *
     * @param auth класс аутентификации spring boot security
     * @return true = AccessToken инициализирован
     */
    fun assign(auth: Authentication?): AccessToken? {

        accessToken = null
        var authentication: Authentication? = null
        // если запрос аутентифицированный, должен быть контекст безопасности, проверим
        if (isSpringContext()) {
            authentication = SecurityContextHolder.getContext().authentication
        } else if (auth != null) {
            authentication = auth
        }

        if (authentication != null) {

            val principal = authentication.principal

            // Проверяем аутентификацию, выполненную по типу > BEARER
            if (principal is Jwt) {
                accessToken = parseAccessToken(principal.tokenValue)
                if (accessToken != null) return accessToken
            }

            // Проверяем аутентификацию, выполненную по типу > JSESSIONID
            if (principal is DefaultOidcUser) {
                accessToken = parseAccessToken(principal.idToken.tokenValue)
                if (accessToken != null) return accessToken
            }
        }
        return accessToken
    }


    /**
     * Метод извлекает экземпляр класса авторизации AccessToken из payload токена доступа keycloak.
     * @param tokenString строка с токеном доступа keycloak, без префикса Bearer
     *
     * @return экземпляр класса AccessToken, или null в случае ошибки
     */
    private fun parseAccessToken(tokenString: String): AccessToken? {

        var accessToken: AccessToken? = null

        if (!StringUtils.isBlank(tokenString)) {

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
        if (accessToken != null) {
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
        if (accessToken != null) {
            try {
                accessToken?.clientRolesMap?.values?.forEach { map ->
                    map.values.forEach { value -> roles.addAll(value.toList()) }
                }
            } catch (ignored: java.lang.Exception) {
            }
        }
        return roles
    }

}