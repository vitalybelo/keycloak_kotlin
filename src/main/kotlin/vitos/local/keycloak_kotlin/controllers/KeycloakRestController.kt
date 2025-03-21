package vitos.local.keycloak_kotlin.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import vitos.local.keycloak_kotlin.services.KeycloakService

@Tag(
    name = "KeycloakRestController",
    description = "API управления учётными данными пользователей и паролями"
)
@Controller
@CrossOrigin
@RequestMapping("/users")
class KeycloakRestController(private val keycloakService: KeycloakService) {

    private val log = LoggerFactory.getLogger(KeycloakRestController::class.java)


    @PostMapping("/change-password")
    @Operation(summary = "Изменение пароля для пользователя в обход установленных политик и ограничений")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Password changed successfully", content = [Content()]),
        ApiResponse(responseCode = "400", description = "Bad request - invalid parameters", content = [Content()]),
        ApiResponse(responseCode = "404", description = "User not found in keycloak", content = [Content()]),
        ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
    ])
    fun changeUserPassword(@RequestParam("user") userName: String,
                           @RequestParam("password") password: String): ResponseEntity<String> {

        if (userName.isNotBlank() && password.isNotBlank()) {
            log.info(">>>> Changing password {}", userName)
            return keycloakService.changeUserPassword(userName, password)
        }
        log.info(">>>> Invalid change password parameters")
        return ResponseEntity("Invalid parameters", HttpStatus.BAD_REQUEST)
    }


    @PostMapping("/create")
    @Operation(summary = "Создание нового пользователя или чтение если он уже существует")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Created successfully", content = [
            (Content(mediaType = "application/json", array = (
                    ArraySchema(schema = Schema(implementation = UserRepresentation::class)))))]),
        ApiResponse(responseCode = "200", description = "User found as already existing", content = [Content()]),
        ApiResponse(responseCode = "400", description = "Invalid request parameters", content = [Content()]),
        ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
    ])
    fun createKeycloakUser(@RequestBody user: UserRepresentation): ResponseEntity<Any> {

        if (!user.username.isNullOrEmpty()) {
            log.info(">>>> Creating user {}", user.username)
            return keycloakService.createKeycloakUser(user)
        }
        log.info(">>>> Invalid create user parameters")
        return ResponseEntity("Invalid username", HttpStatus.BAD_REQUEST)
    }


    @PostMapping("/well-known")
    @Operation(summary = "Возвращает информацию о конечных точках сервера")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Выполнено успешно", content = [Content()]),
        ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
    ])
    fun wellKnownKeycloak(): ResponseEntity<Any> {
        return keycloakService.getWellKnownEndPoints()
    }

}