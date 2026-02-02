package vitos.local.keycloak_kotlin.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * Класс для получения списка всех ролей пользователя
 * @author Vitalii Belotserkovskii 02.02.2026
 */
@Tag(
    name = "CompositeRoles",
    description = "List of all effective user roles"
)
@Schema(description = "List of all effective user roles")
@JsonIgnoreProperties(ignoreUnknown = true)
data class CompositeRoles(

    var id: String? = null,
    var role: String? = null,
    var client: String? = null,
    var clientId: String? = null,
    var description: String? = null

)