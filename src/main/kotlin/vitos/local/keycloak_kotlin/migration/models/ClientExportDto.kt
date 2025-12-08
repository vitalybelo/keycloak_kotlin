package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.keycloak.representations.idm.ClientRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.keycloak.representations.idm.authorization.ResourceServerRepresentation

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientExportDto(

    var clientRepresentation: ClientRepresentation? = null,
    var serviceAccountUser: UserRepresentation? = null,
    var clientRoles: List<RoleRepresentation>? = null,
    var exportSettings: ResourceServerRepresentation? = null
)