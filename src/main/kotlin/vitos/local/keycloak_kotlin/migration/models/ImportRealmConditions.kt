package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RolesRepresentation

/**
 * Data класс, обеспечивающий выполнение partial import области сервисов
 * @author Belotserkovskii Vitaly (c) 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ImportRealmConditions(

    var isMigrateRealmRoles: Boolean = false,
    var isMigrateClientScopes: Boolean = false,
    var isMigrateRealmGroups: Boolean = false,
    var isMigrateFlows: Boolean = false,

    var importFlowsDto: ImportFlowDto = ImportFlowDto(),
    var clientScopes: ClientScopeExportDto = ClientScopeExportDto(),
    var groups: List<GroupRepresentation>? = null,
    var roles: RolesRepresentation? = null

) {

    fun isPartialNeed(): Boolean {
        return isMigrateClientScopes || isMigrateRealmRoles || isMigrateRealmGroups || isMigrateFlows
    }

    fun copyAndClear(importedRepresentation: RealmRepresentation) {

        duplicate(importedRepresentation)

        // для создания или обновления realm это нужно обнулить, шагом выше мы сохранили эти данные
        // на тот случай, если требуется выполнить частичный импорт roles, scopes, groups, flows
        importedRepresentation.groups = null
        importedRepresentation.roles = null
        importedRepresentation.clientScopes = null
        importedRepresentation.authenticationFlows = null
        importedRepresentation.authenticatorConfig = null
        importedRepresentation.clients = null
        importedRepresentation.users = null
    }

    private fun duplicate(importedRepresentation: RealmRepresentation) {

        if (isMigrateRealmGroups) {
            this.groups = importedRepresentation.groups
        }
        if (isMigrateRealmRoles) {
            this.roles = importedRepresentation.roles
        }
        if (isMigrateClientScopes) {
            this.clientScopes.clientScopes = importedRepresentation.clientScopes ?: emptyList()
            this.clientScopes.default = importedRepresentation.defaultDefaultClientScopes ?: emptyList()
            this.clientScopes.optional= importedRepresentation.defaultOptionalClientScopes ?: emptyList()
        }
        if (isMigrateFlows) {
            this.importFlowsDto.authenticationFlows = importedRepresentation.authenticationFlows ?: mutableListOf()
            this.importFlowsDto.authenticatorConfigs = importedRepresentation.authenticatorConfig ?: mutableListOf()
        }
    }

}