package vitos.local.keycloak_kotlin.models

import vitos.local.keycloak_kotlin.constants.Constants.Companion.RESPONSE_DATE_TIME_FORMATTER
import java.time.LocalDateTime


data class ApiErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: String = LocalDateTime.now().format(RESPONSE_DATE_TIME_FORMATTER)
)