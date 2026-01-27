package vitos.local.keycloak_kotlin.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * Класс для получения информации о статусе блокировки
 * @author Vitalii Belotserkovskii 09.04.2025
 */
@Tag(
    name = "BruteForceStatus",
    description = "Brute-force статус class"
)
@Schema(description = "Brute-force статус")
@JsonIgnoreProperties(ignoreUnknown = true)
data class BruteForceStatus(

    @JsonProperty("numFailures") var numFailures: Int? = null,
    @JsonProperty("disabled") var disabled: Boolean? = null,
    @JsonProperty("lastIPFailure") var lastIPFailure: String? = null,
    @JsonProperty("lastFailure") var lastFailure: Long? = null

)