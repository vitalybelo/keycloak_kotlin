package vitos.local.keycloak_kotlin.models

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "migrate_exchange", schema = "keycloakadp")
class MigrateExchange(

    @Id
    @Column(name = "ID", nullable = false, length = 36)
    var id: String? = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(name = "JSON_TYPE", length = 20, nullable = true)
    var jsonType: JsonType? = null,

    @Column(name = "JSON_PAYLOAD", length = Integer.MAX_VALUE, nullable = true)
    var jsonPayload: String? = null,

    @Column(name = "REALM", nullable = true)
    var realm: String? = null,

    @CreatedDate
    @Column(name = "CREATED_DATE", columnDefinition = "TIMESTAMP WITH TIME ZONE", updatable = false)
    var createdDate: ZonedDateTime? = null

) {

    constructor(realm: String?, jsonType: JsonType?, jsonPayload: String?) : this() {
        this.realm = realm
        this.jsonType = jsonType
        this.jsonPayload = jsonPayload
        this.createdDate = ZonedDateTime.now()
    }
}