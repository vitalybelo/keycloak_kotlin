package vitos.local.keycloak_kotlin.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Расширенный класс для получения информации о блокировках
 * @author Vitalii Belotserkovskii 09.04.2025
 */
@Schema(description = "Сущность пользователя keycloak с информацией по блокировкам brute-force")
@JsonIgnoreProperties(ignoreUnknown = true)
data class BruteForceUserRepresentation(

    @JsonProperty("id") var id: String? = null,
    @JsonProperty("username") var username: String? = null,
    @JsonProperty("firstName") var firstName: String? = null,
    @JsonProperty("lastName") var lastName: String? = null,
    @JsonProperty("email") var email: String? = null,
    @JsonProperty("emailVerified") var emailVerified: Boolean? = null,
    @JsonProperty("attributes") var attributes: LinkedHashMap<String, List<String>>? = null,
    @JsonProperty("enabled") var enabled: Boolean? = null,
    @JsonProperty("createdTimestamp") var createdTimestamp: Long? = null,
    @JsonProperty("totp") var temporaryOTP: Boolean? = null,
    @JsonProperty("disableableCredentialTypes") var disableCredentialTypes: List<String>? = null,
    @JsonProperty("requiredActions") var requiredActions: List<String>? = null,
    @JsonProperty("notBefore") var notBefore: Int? = null,
    @JsonProperty("access") var access: LinkedHashMap<String, Boolean>? = null,
    @JsonProperty("bruteForceStatus") var bruteForceStatus: BruteForceStatus? = null

)