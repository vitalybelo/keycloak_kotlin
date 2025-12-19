package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.keycloak.representations.idm.AuthenticationFlowRepresentation
import org.keycloak.representations.idm.AuthenticatorConfigRepresentation

/**
 * Класс данных сбора конфигурации потока аутентификации
 * @author Belotserkovskii Vitaly (c) 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FlowExportDto(

    var totalRootFlows: Int = 0,
    var totalFlows: Int = 0,
    var totalConfigurations: Int = 0,
    var authenticationFlows: MutableList<AuthenticationFlowRepresentation> = mutableListOf(),
    var authenticatorConfigs: MutableList<AuthenticatorConfigRepresentation> = mutableListOf()
)

/**
 * Для удобства сбора потоков и конфигураций, будем использовать карту, ключом в которой
 * будет uuid идентификатор потока или конфиграции, чтобы сформировать уникальный список
 * результатов - список потоков и список конфигураций
 */
data class CollectFlowDto(

    var rootFlowsCount: Int = 0,
    var flowsMap: MutableMap<String, AuthenticationFlowRepresentation> = mutableMapOf(),
    var configsMap: MutableMap<String, AuthenticatorConfigRepresentation> = mutableMapOf()
) {

    fun addFlow(flow: AuthenticationFlowRepresentation) {
        val id = flow.id
        if (!flowsMap.contains(id)) {
            flowsMap[id] = flow
        }
    }

    fun getExportDto(): FlowExportDto {

        val exportDto = FlowExportDto()
        if (flowsMap.isNotEmpty()) {
            exportDto.authenticationFlows = flowsMap.values.toMutableList()
        }
        if (configsMap.isNotEmpty()) {
            exportDto.authenticatorConfigs = configsMap.values.toMutableList()
        }
        exportDto.totalFlows = flowsMap.size
        exportDto.totalRootFlows = rootFlowsCount
        exportDto.totalConfigurations = configsMap.size
        return exportDto
    }
}