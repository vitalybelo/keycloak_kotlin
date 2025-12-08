package vitos.local.keycloak_kotlin.migration.services.keycloak

import org.keycloak.admin.client.Keycloak
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service

@Service
class KeycloakTokenService(
    private val keycloak: Keycloak
) {

    /**
     * Метод запрашивает токен доступа к административной консоли keycloak, и после получения
     * формирует заголовок для Oauth2 аутентификации с bearer токеном
     *
     * @return экземпляр класса заголовка http запроса HttpHeaders
     */
    fun getOauth2Headers(): HttpHeaders {
        val httpHeaders = HttpHeaders()
        httpHeaders.add("Authorization", "Bearer ${keycloak.tokenManager().accessTokenString}")
        return httpHeaders
    }
}