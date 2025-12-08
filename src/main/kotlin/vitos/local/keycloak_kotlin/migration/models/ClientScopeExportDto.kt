package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.keycloak.representations.idm.ClientScopeRepresentation

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientScopeExportDto(

    var clientScopes: List<ClientScopeRepresentation>? = null,
    var default: List<String>? = null,
    var optional: List<String>? = null
)