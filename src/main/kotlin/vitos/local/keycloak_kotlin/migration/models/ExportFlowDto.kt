package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.keycloak.representations.idm.AuthenticationFlowRepresentation
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation

/**
 * Класс данных сбора конфигурации потока аутентификации
 * @author Belotserkovskii Vitaly (c) 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExportFlowDto(

    var authenticationFlows: MutableList<AuthenticationFlowRepresentation> = mutableListOf(),
    var authenticatorConfigs: MutableList<AuthenticatorConfigRepresentation> = mutableListOf()
)

data class CollectFlowDto(

    var flowsMap: MutableMap<String, AuthenticationFlowRepresentation> = mutableMapOf(),
    var configsMap: MutableMap<String, AuthenticatorConfigRepresentation> = mutableMapOf()
) {

    fun getExportDto(): ExportFlowDto {

        val exportDto = ExportFlowDto()
        if (flowsMap.isNotEmpty()) {
            exportDto.authenticationFlows = flowsMap.values.toMutableList()
        }
        if (configsMap.isNotEmpty()) {
            exportDto.authenticatorConfigs = configsMap.values.toMutableList()
        }
        return exportDto
    }
}