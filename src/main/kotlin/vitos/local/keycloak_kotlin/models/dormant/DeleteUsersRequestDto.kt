package vitos.local.keycloak_kotlin.models.dormant

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * ДТО класс запроса на групповое удаление пользователей
 * @author Belotserkovskii Vitaly
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DeleteUsersRequestDto(

    @field:Schema(description = "Список IDs пользователей с которыми должна выполняться логика удаления")
    @param:JsonProperty("abscust_id")
    val abscustId: List<String?>? = null,

    @field:Schema(description = "Заглушка для тестирования чтобы не удалять пользователей из Keycloak")
    @param:JsonProperty("is_delete", required = false)
    val isHardDelete: Boolean? = true

) {

    fun getValueSet(): Set<String>? {

        if (!abscustId.isNullOrEmpty()) {
            val values = abscustId.filterNotNull().toSet()
            if (values.isNotEmpty()) {
                return values
            }
        }
        return null
    }
}