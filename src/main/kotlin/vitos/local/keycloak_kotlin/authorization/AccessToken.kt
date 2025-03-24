package vitos.local.keycloak_kotlin.authorization

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Класс описывающий сущность токена доступа пользователя keycloak. Поля по необходимости можно добавлять.
 * @author Vitalii Belotserkovskii, 24.03.2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessToken(

    var exp: String? = null,
    var iat: String? = null,
    var jti: String? = null,
    var iss: String? = null,
    var aud: String? = null,

    @JsonProperty("sub")
    var userId: String? = null,

    var typ: String? = null,

    @JsonProperty("azp")
    var clientId: String? = null,

    @JsonProperty("sid")
    var sessionId: String? = null,

    @JsonProperty("session_state")
    var sessionState: String? = null,

    @JsonProperty("realm_access")
    var realmRolesMap: LinkedHashMap<String, List<String>>? = null,

    @JsonProperty("resource_access")
    var clientRolesMap: LinkedHashMap<String, LinkedHashMap<String, List<String>>>? = null,

    @JsonProperty("given_name")
    var firstName: String? = null,

    @JsonProperty("middle_name")
    var middleName: String? = null,

    @JsonProperty("family_name")
    var familyName: String? = null,

    var name: String? = null,

    @JsonProperty("preferred_username")
    var login: String? = null,

    var email: String? = null,
    var phone: String? = null,
    var department: String? = null,
    var position: String? = null,

    @JsonProperty("email_verified")
    var emailVerified: Boolean = false,

    )