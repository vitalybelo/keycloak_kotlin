package vitos.local.keycloak_kotlin.interfaces

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping

@Tag(
    name = "MigrationRestController",
    description = "API для сохранения и восстановления настроек Keycloak"
)
@RequestMapping("/migrate")
interface MigrationRestController {


    /**
     * Выполняет чтение списка сущностей маппинга для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов для которой нужно выполнить чтение ClientScopes
     * @return статус выполнения и список сущностей, либо сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Client Scopes List received successfully", content = [Content()]),
            ApiResponse(responseCode = "400", description = "Invalid request parameter - realm name", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Not found any client scopes in realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/client-scopes")
    @Operation(summary = "Изменение пароля для пользователя в обход установленных политик и ограничений")
    fun getAllRealmClientScopes(
        @PathVariable("realm") realm: String?,
    ): ResponseEntity<Any>

}