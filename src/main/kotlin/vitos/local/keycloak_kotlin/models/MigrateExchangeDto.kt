package vitos.local.keycloak_kotlin.models

import java.io.Serializable
import java.time.ZonedDateTime
import java.util.*

data class MigrateExchangeDto(

    val id: String? = null,
    val jsonType: JsonType? = null,
    val jsonPayload: String? = null,
    val realm: String? = null,
    val createdDate: ZonedDateTime? = null

) : Serializable