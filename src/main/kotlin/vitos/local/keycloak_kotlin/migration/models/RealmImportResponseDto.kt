package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.keycloak.representations.idm.RealmRepresentation


/**
 * Дто ответа за запрос создания или обновления сущностей realm настроек
 * @author Belotserkovskii Vitaly (с) 2025
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RealmImportResponseDto(

    var rolesCount: Int = 0,
    var scopesCount: Int = 0,
    var groupsCount: Int = 0,
    var flowsCount: Int = 0,
    var representation: RealmRepresentation? = null
)
