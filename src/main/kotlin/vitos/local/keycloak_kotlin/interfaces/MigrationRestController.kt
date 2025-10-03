package vitos.local.keycloak_kotlin.interfaces

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
     * @param realm название области сервисов
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
    @Operation(summary = "Читает настройки маппинга, сделанные для Client Scopes в заданном realm")
    fun getAllRealmClientScopes(@PathVariable("realm", required = true) realm: String): ResponseEntity<Any>


    /**
     * Выполняет добавление маппинга в Client Scopes для заданной параметром области сервисов.
     * Список сущностей для маппинга метод получает как параметр, переданный в теле запроса
     *
     * @param realm название области сервисов
     * @param clientScopes список сущностей маппинга Client Scopes
     * @return статус выполнения и отчет
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Client Scopes successfully added", content = [Content()]),
            ApiResponse(responseCode = "400", description = "Invalid request parameter - realm name or client scope list", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/client-scopes")
    @Operation(summary = "Устанавливает настройки маппинга, для Client Scopes в заданном realm")
    fun updateAllRealmClientScopes(
        @PathVariable("realm", required = true) realm: String,
        @RequestBody(required = true) clientScopes: List<ClientScopeRepresentation>
        ): ResponseEntity<Any>


    /**
     * Выполняет чтение списка всех сущностей Clients для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов
     * @return статус выполнения, список сервисов Clients - либо сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Client List received successfully", content = [Content()]),
            ApiResponse(responseCode = "400", description = "Invalid request parameter - realm name", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Not found any clients in realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/clients/{client_id}")
    @Operation(summary = "Читает данные по Clients в заданном realm")
    fun getAllRealmClients(
        @PathVariable("realm", required = true) realm: String,
        @PathVariable("client_id", required = true) clientId: String): ResponseEntity<Any>


    /**
     * Выполняет чтение списка всех существующих realm ролей для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов
     * @return статус выполнения, список сервисов Realm Roles - либо сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Realm Roles List received successfully", content = [Content()]),
            ApiResponse(responseCode = "400", description = "Invalid request parameter - realm name", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Not found any Realm Roles in realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/realm-roles")
    @Operation(summary = "Читает все realm roles для заданной параметром области сервисов")
    fun getAllRealmRoles(@PathVariable("realm", required = true) realm: String): ResponseEntity<Any>


    /**
     * Выполняет создание или обновление существующих realm ролей для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов
     * @param realmRoleList список realm roles для создания или изменения
     * @return статус выполнения или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Realm Roles List added|updated successfully", content = [Content()]),
            ApiResponse(responseCode = "400", description = "Invalid request parameter - realm name or realm roles list", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/realm-roles")
    @Operation(summary = "Создает realm roles в заданной параметром области сервисов")
    fun createOrUpdateAllRealmRoles(
        @PathVariable(required = true, value = "realm") realm: String,
        @RequestBody(required = true) realmRoleList: List<RoleRepresentation>): ResponseEntity<Any>

}