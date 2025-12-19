package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.RolesRepresentation
import vitos.local.keycloak_kotlin.logging.Log

/**
 * Data класс, обеспечивающий выполнение partial import области сервисов
 * @author Belotserkovskii Vitaly (c) 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RealmImportConditions(

    var isMigrateRealmRoles: Boolean = false,
    var isMigrateClientScopes: Boolean = false,
    var isMigrateRealmGroups: Boolean = false,
    var isMigrateFlows: Boolean = false,

    var defaultGroups: List<String>? = null,
    var defaultRole: RoleRepresentation? = null,
    var importFlowsDto: FlowImportDto = FlowImportDto(),
    var clientScopes: ClientScopeExportDto = ClientScopeExportDto(),
    var groups: List<GroupRepresentation>? = null,
    var roles: RolesRepresentation? = null

) {

    companion object: Log()

    fun isPartialNeed(): Boolean {
        return isMigrateClientScopes || isMigrateRealmRoles || isMigrateRealmGroups || isMigrateFlows
    }

    fun copyAndClear(importedRepresentation: RealmRepresentation) {

        duplicate(importedRepresentation)

        // для создания или обновления realm это нужно обнулить, шагом выше мы сохранили эти данные
        // на тот случай, если требуется выполнить частичный импорт roles, scopes, groups, flows
        importedRepresentation.roles = null
        importedRepresentation.groups = null
        importedRepresentation.clientScopes = null
        importedRepresentation.authenticationFlows = null
        importedRepresentation.authenticatorConfig = null
        importedRepresentation.clients = null
        importedRepresentation.users = null
        importedRepresentation.defaultRole = null
        importedRepresentation.defaultGroups = null
    }

    /**
     * Для создания или обновления рабочей области, нам приходится занулить (как сделано выше) некоторые
     * данные, а затем назначить (вернуть) через специально созданные для этого методы. Роли, группы, потоки,
     * маппинги, а также набор дефолтных групп, мы восстановим после создания или обновления настроек realm
     * @param importedRepresentation импортируемая сущность настроек realm
     */
    private fun duplicate(importedRepresentation: RealmRepresentation) {

        defaultRole = importedRepresentation.defaultRole
        defaultGroups = importedRepresentation.defaultGroups

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