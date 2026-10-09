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
    @param:JsonProperty("abscust_id", required = true)
    val abscustId: List<String?>,

    @field:Schema(description = "Заглушка для тестирования чтобы не удалять пользователей из Keycloak")
    @param:JsonProperty("is_delete", required = false, defaultValue = "true")
    val isHardDelete: Boolean

) {

    fun getValueSet(): Set<String> = abscustId.filterNotNull().toSet()
}