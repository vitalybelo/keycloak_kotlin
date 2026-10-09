package vitos.local.keycloak_kotlin.authorization

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Класс описывающий сущность токена доступа пользователя keycloak. Поля по необходимости можно добавлять.
 * @author Vitalii Belotserkovskii, 24.03.2025
 */
@Suppress("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccessToken(

    val exp: Long?,
    val iat: Long?,
    val jti: String?,
    val iss: String?,
    val aud: List<String>?,

    @JsonProperty("sub") val userId: String?,
    @JsonProperty("typ") val type: String?,
    @JsonProperty("azp") val clientId: String?,
    @JsonProperty("sid") val sessionId: String?,
    @JsonProperty("session_state") val sessionState: String?,

    @JsonProperty("realm_access")
    val realmRolesMap: LinkedHashMap<String, List<String>>? = null,

    @JsonProperty("resource_access")
    val clientRolesMap: LinkedHashMap<String, LinkedHashMap<String, List<String>>>?,

    @JsonProperty("scope") val scope: String?,
    @JsonProperty("given_name") val firstName: String?,
    @JsonProperty("middle_name") val middleName: String?,
    @JsonProperty("family_name") val familyName: String?,
    @JsonProperty("name") val displayName: String?,
    @JsonProperty("preferred_username") val login: String?,
    @JsonProperty("email_verified") val emailVerified: Boolean?,

    val email: String?,
    val phone: String?,
    val department: String?,
    val position: String?

) {
    companion object {
        const val SPACE = " "
    }
    /**
     * @return Возвращает полное имя пользователя: имя, отчество и фамилию
     */
    fun fullName(): String = buildString {
        append(firstName ?: login ?: "Anonymous")
        if (!middleName.isNullOrBlank()) append(SPACE).append(middleName)
        if (!familyName.isNullOrBlank()) append(SPACE).append(familyName)
    }

}