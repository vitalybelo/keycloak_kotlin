package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientListExportDto(

    var successExported: MutableList<String> = mutableListOf(),
    var notFound: MutableList<String> = mutableListOf(),
    var clients: MutableList<ClientExportDto> = mutableListOf()
) {

    fun addSuccess(clientExportDto: ClientExportDto) {
        clients.add(clientExportDto)
        val clientId = clientExportDto.clientRepresentation?.clientId
        if (clientId != null) {
            successExported.add(clientId)
        }
    }

    fun addNotFound(clientId: String) {
        notFound.add(clientId)
    }

}