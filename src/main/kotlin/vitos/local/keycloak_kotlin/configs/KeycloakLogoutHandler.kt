package vitos.local.keycloak_kotlin.configs

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder


@Component
class KeycloakLogoutHandler(
    private val restTemplate: RestTemplate?
) : LogoutHandler {

    private val logger: Logger = LoggerFactory.getLogger(KeycloakLogoutHandler::class.java)

    override fun logout(request: HttpServletRequest?, response: HttpServletResponse?, auth: Authentication) {
        logoutFromKeycloak(auth.principal as OidcUser)
    }

    /**
     * Метод реализует выход из keycloak запросом по back channel
     * @param user - информация о пользователе, id_token + утверждения из jwt токена
     */
    private fun logoutFromKeycloak(user: OidcUser) {
        val endSessionEndpoint = user.issuer.toString() + "/protocol/openid-connect/logout"
        val clientId = user.getClaimAsString("azp")
        val builder = UriComponentsBuilder
            .fromUriString(endSessionEndpoint)
            .userInfo(user.userInfo.toString())
            .queryParam("client_id", clientId)
            .queryParam("logout_hint", user.name)
            .queryParam("id_token_hint", user.idToken.tokenValue)

        val logoutResponse = restTemplate!!.getForEntity(builder.toUriString(), String::class.java)

        if (logoutResponse.statusCode.is2xxSuccessful) {
            logger.info("Successfully logged out from Keycloak")
        } else {
            logger.error("Could not propagate logout to Keycloak")
        }
    }


}