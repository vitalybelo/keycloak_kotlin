package vitos.local.keycloak_kotlin.models.migrate

import vitos.local.keycloak_kotlin.models.JsonType
import java.io.Serializable
import java.time.ZonedDateTime

@Suppress("unused")
data class MigrateExchangeDto(

    val id: String? = null,
    val jsonType: JsonType? = null,
    val jsonPayload: String? = null,
    val realm: String? = null,
    val createdDate: ZonedDateTime? = null

) : Serializable