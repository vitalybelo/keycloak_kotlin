package vitos.local.keycloak_kotlin.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util.Locale


/**
 * Класс отправки сообщения в ms-data-export об удалении пользователя из Keycloak
 * @author Belotserkovskii Vitaly
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class DeleteUsersEventDto(

    @field:Schema(description = "ID пользователя с которым должна выполняться логика удаления")
    @param:JsonProperty("abscustid")
    var abscustId: String? = null,

    @field:Schema(description = "Дата отправки сообщения")
    @param:JsonProperty("datetime")
    var dateTime: String? = null,

    @field:Schema(description = "Тип удаления - MAN ручное, DOR - инициирован DORMANT")
    @param:JsonProperty("type")
    var type: String = "DOR",

    @field:Schema(description = "Атрибуты пользователя которые нужно передавать в data-export")
    @param:JsonProperty("attributes")
    var attributes: String? = null

) {

    constructor(abscustId: String) : this() {
        this.abscustId = abscustId
        this.dateTime = ZonedDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH))
            .toString()
    }

    fun toDebugString(): String {
        return """
            DataExportEventDto
            {
                abscustid=$abscustId,
                datetime=$dateTime,
                type='$type',
                attributes=$attributes
            }
            """
    }
}