package vitos.local.keycloak_kotlin.interfaces

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/public")
interface PublicRestController {

    /**
     * Возвращает данные о конечных точках сервера авторизации Keycloak - в упрощенном виде
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Данные о конечных точках получены успешно", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @GetMapping("/well-known")
    @Operation(summary = "Возвращает информацию о конечных точках сервера")
    fun getKeycloakWellKnown(): ResponseEntity<Any>

}