package vitos.local.keycloak_kotlin.configs

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate



@Configuration
class KeycloakAdminConfiguration (

    @Value("\${keycloak.server.url:http://localhost:8443}")
    private val keycloakServerUrl: String,
    @Value("\${keycloak.realm:SpringBootKeycloak}")
    private val keycloakRealm: String,
    @Value("\${keycloak.admin.client_id:login-admin}")
    private val keycloakAdminClientId: String,
    @Value("\${keycloak.admin.client_secret}")
    private val keycloakAdminClientSecret: String

) {

    @Bean
    fun realmResource(): RealmResource {
        return KeycloakBuilder.builder()
            .serverUrl(keycloakServerUrl)
            .realm(keycloakRealm)
            .clientId(keycloakAdminClientId)
            .clientSecret(keycloakAdminClientSecret)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .build().realm(keycloakRealm)
    }

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

}