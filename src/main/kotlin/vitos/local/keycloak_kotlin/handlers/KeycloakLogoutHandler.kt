package vitos.local.keycloak_kotlin.handlers

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.util.UriComponentsBuilder

@Component
class KeycloakLogoutHandler(
    private val restTemplate: RestTemplate?
): LogoutHandler {

    private val logger: Logger = LoggerFactory.getLogger(KeycloakLogoutHandler::class.java)

    override fun logout(request: HttpServletRequest,
                        response: HttpServletResponse,
                        authentication: Authentication?) {
        logoutFromKeycloak(authentication)
    }

    /**
     * Метод реализует выход из keycloak запросом по back channel
     * @param authentication - класс аутентификации Spring Boot Security
     */
    private fun logoutFromKeycloak(authentication: Authentication?) {

        val user = authentication?.principal as? OidcUser ?: return
        val endSessionEndpoint = user.issuer.toString() + "/protocol/openid-connect/logout"
        val clientId = user.getClaimAsString("azp")
        val builder = UriComponentsBuilder
            .fromUriString(endSessionEndpoint)
            .userInfo(user.userInfo.toString())
            .queryParam("client_id", clientId)
            .queryParam("logout_hint", user.name)
            .queryParam("id_token_hint", user.idToken.tokenValue)

        val logoutResponse = restTemplate!!.getForEntity<String>(builder.toUriString())

        if (logoutResponse.statusCode.is2xxSuccessful) {
            logger.info("Successfully logged out from Keycloak")
        } else {
            logger.error("Could not propagate logout to Keycloak")
        }
    }


}