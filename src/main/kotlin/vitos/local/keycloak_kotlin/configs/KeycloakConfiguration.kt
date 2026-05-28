package vitos.local.keycloak_kotlin.configs

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.admin.client.resource.RealmResource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate


@Configuration
class KeycloakConfiguration (

    @param:Value($$"${keycloak.server.url:http://localhost:8443}")
    private val keycloakServerUrl: String,
    @param:Value($$"${keycloak.admin.realm:SpringBootKeycloak}")
    private val keycloakRealm: String,
    @param:Value($$"${keycloak.admin.client_id:login-admin}")
    private val keycloakAdminClientId: String,
    @param:Value($$"${keycloak.admin.client_secret}")
    private val keycloakAdminClientSecret: String,
    @param:Value($$"${keycloak.master.admin.realm}")
    private val keycloakMasterAdminRealm: String,
    @param:Value($$"${keycloak.master.admin.client-id}")
    private val keycloakMasterAdminClientId: String,
    @param:Value($$"${keycloak.master.admin.secret}")
    private val keycloakMasterAdminClientSecret: String,
) {

    @Bean
    @Qualifier("keycloakMaster")
    fun keycloakMaster(): Keycloak {
        return KeycloakBuilder.builder()
            .serverUrl(keycloakServerUrl)
            .realm(keycloakMasterAdminRealm)
            .clientId(keycloakMasterAdminClientId)
            .clientSecret(keycloakMasterAdminClientSecret)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .build()
    }

    @Bean
    @Qualifier("keycloakRealm")
    fun keycloakRealm(): Keycloak {
        return KeycloakBuilder.builder()
            .serverUrl(keycloakServerUrl)
            .realm(keycloakRealm)
            .clientId(keycloakAdminClientId)
            .clientSecret(keycloakAdminClientSecret)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .build()
    }

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