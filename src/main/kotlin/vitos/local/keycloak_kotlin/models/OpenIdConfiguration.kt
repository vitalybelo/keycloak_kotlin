package vitos.local.keycloak_kotlin.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Класс для сопоставления json ответа из конечной точки сервиса keycloak /.well-known/openid-configuration
 * @author Vitali Belotserkovskii, 25.03.2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenIdConfiguration(

    @JsonProperty("issuer")
    var issuer: String? = null,

    @JsonProperty("authorization_endpoint")
    var authorizationURL: String? = null,

    @JsonProperty("token_endpoint")
    var tokenURL: String? = null,

    @JsonProperty("introspection_endpoint")
    var introspectionURL: String? = null,

    @JsonProperty("userinfo_endpoint")
    var userInfoURL: String? = null,

    @JsonProperty("end_session_endpoint")
    var logoutURL: String? = null,

    @JsonProperty("jwks_uri")
    var certsURL: String? = null,

    @JsonProperty("grant_types_supported")
    var grandTypes: List<String>? = null

)