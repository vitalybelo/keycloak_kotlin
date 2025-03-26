package vitos.local.keycloak_kotlin.interfaces

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam


interface KeycloakRestController {


    /**
     * Выполняет замену пароля для заданного в запросе пользователя. Метод учитывает наличие
     * настроек политик безопасности для сброса паролей, поэтому вначале сохраняет действующие
     * политики, затем обнуляет политики для области (realm), выполняет сброс пароля на любой
     * заданный и затем восстанавливает дефолтные для области
     *
     * @param userName - username пользователя
     * @param password - новый пароль пользователя
     * @param authentication - класс аутентификации spring security
     * @return строку сообщения о выполнении и статус выполнения
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Password changed successfully", content = [Content()]),
            ApiResponse(responseCode = "404", description = "User not found in keycloak", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/change-password")
    @Operation(summary = "Изменение пароля для пользователя в обход установленных политик и ограничений")
    fun changeUserPassword(
        @RequestParam("user", required = false) userName: String? = null,
        @RequestParam("password", required = false, defaultValue = "1") password: String? = "1",
        authentication: Authentication
    ): ResponseEntity<Any>


    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created successfully", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = UserRepresentation::class)))
                    ))]
            ),
            ApiResponse(responseCode = "200", description = "User found as already existing", content = [Content()]),
            ApiResponse(responseCode = "400", description = "Invalid request parameters", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/create")
    @Operation(summary = "Создание нового пользователя или чтение если он уже существует")
    fun createKeycloakUser(@RequestBody user: UserRepresentation): ResponseEntity<Any>


    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Created successfully", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @PostMapping("/well-known")
    @Operation(summary = "Возвращает информацию о конечных точках сервера")
    fun wellKnownKeycloak(): ResponseEntity<Any>

}