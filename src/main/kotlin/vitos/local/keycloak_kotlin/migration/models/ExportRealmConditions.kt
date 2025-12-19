package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Data класс, обеспечивающий выполнение частичного экспорта области сервисов
 * @author Belotserkovskii Vitaly (c) 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExportRealmConditions(

    var isMigrateRealmRoles: Boolean = false,
    var isMigrateClientScopes: Boolean = false,
    var isMigrateRealmGroups: Boolean = false,
    var isMigrateFlows: Boolean = false,

)