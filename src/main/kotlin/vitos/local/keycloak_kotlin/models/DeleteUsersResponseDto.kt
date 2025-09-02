package vitos.local.keycloak_kotlin.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * ДТО класс ответа на запрос о групповом удалении пользователей
 * @author Belotserkovskii Vitaly
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DeleteUsersResponseDto(

    @field:Schema(description = "ID пользователя с которым выполнялась логика удаления")
    @param:JsonProperty("abscust_id")
    val abscustId: String? = null,

    @field:Schema(
        description = """Статус выполнения удаления:
        | 2   - пользователя нет в Keycloak / пользователь найден и удален успешно,
        | 3   - не требуется удаление пользователя (он найден, обнаружен вход в установленный период) 
        | 999 - пользователь найден, но при выполнении удаления произошла непредвиденная ошибка
        | """
    )
    @param:JsonProperty("status")
    val status: Int? = null

)