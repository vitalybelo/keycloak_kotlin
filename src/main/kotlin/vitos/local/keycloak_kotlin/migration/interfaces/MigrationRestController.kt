package vitos.local.keycloak_kotlin.migration.interfaces

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import vitos.local.keycloak_kotlin.migration.models.ClientExportDto
import vitos.local.keycloak_kotlin.migration.models.ClientScopeExportDto
import vitos.local.keycloak_kotlin.migration.models.ImportFlowDto

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
            ApiResponse(
                responseCode = "200",
                description = "Client Scopes List received successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found any client scopes in realm",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/client-scopes")
    @Operation(summary = "Читает настройки маппинга, сделанные для Client Scopes в заданном realm")
    fun getAllRealmClientScopes(
        @PathVariable("realm", required = true) realm: String
    ): ResponseEntity<Any>


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
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name or client scope list",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/client-scopes")
    @Operation(summary = "Устанавливает настройки маппинга, для Client Scopes в заданном realm")
    fun updateAllRealmClientScopes(
        @PathVariable("realm", required = true) realm: String,
        @RequestBody(required = true) clientScopes: ClientScopeExportDto
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
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name",
                content = [Content()]
            ),
            ApiResponse(responseCode = "404", description = "Not found any clients in realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/clients/{client_id}")
    @Operation(summary = "Читает расширенные данные Clients в заданном realm")
    fun getRealmClient(
        @PathVariable("realm", required = true) realm: String,
        @PathVariable("client_id", required = true) clientId: String
    ): ResponseEntity<Any>


    /**
     * Выполняет создание или обновление сервиса в Clients для заданной входным параметром области сервисов Realm.
     *
     * @param realm название области сервисов
     * @param client экспортная сущность нового сервиса
     * @return статус выполнения или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm Roles List added|updated successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name or realm roles list",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/clients")
    @Operation(summary = "Создает сервис Client в заданной параметром области сервисов realm")
    fun createOrUpdateRealmClient(
        @RequestParam("isAlwaysCreate", required = false, defaultValue = "true") isAlwaysCreate: Boolean,
        @PathVariable(required = true, value = "realm") realm: String,
        @RequestBody(required = true) client: ClientExportDto
    ): ResponseEntity<Any>


    /**
     * Выполняет чтение списка всех существующих realm ролей для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов
     * @return статус выполнения, список сервисов Realm Roles - либо сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm Roles List received successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found any Realm Roles in realm",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/realm-roles")
    @Operation(summary = "Читает все realm roles для заданной параметром области сервисов")
    fun getAllRealmRoles(
        @PathVariable("realm", required = true) realm: String
    ): ResponseEntity<Any>


    /**
     * Выполняет создание или обновление существующих realm ролей для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов
     * @param realmRoleList список realm roles для создания или изменения
     * @return статус выполнения или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm Roles List added|updated successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name or realm roles list",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/realm-roles")
    @Operation(summary = "Создает realm roles в заданной параметром области сервисов")
    fun createOrUpdateAllRealmRoles(
        @PathVariable(required = true, value = "realm") realm: String,
        @RequestBody(required = true) realmRoleList: List<RoleRepresentation>
    ): ResponseEntity<Any>


    /**
     * Выполняет чтение настроек области сервисов realm
     *
     * @param realm название области сервисов
     * @return статус выполнения, сущность настроек или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm configuration received successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name",
                content = [Content()]
            ),
            ApiResponse(responseCode = "404", description = "Not found preassigned realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/configuration")
    @Operation(summary = "Читает конфигурацию для заданной параметром области сервисов realm")
    fun getRealmConfiguration(
        @RequestHeader(name = "User-Agent", required = true) userAgent: String,
        @PathVariable("realm", required = true) realm: String
    ): ResponseEntity<Any>


    /**
     * Выполняет изменение настроек области сервисов realm
     *
     * @param realm название области сервисов
     * @param representation сущность новых настроек для области
     * @return статус выполнения, сущность настроек или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm configuration updated successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name",
                content = [Content()]
            ),
            ApiResponse(responseCode = "404", description = "Not found preassigned realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/configuration")
    @Operation(summary = "Вносит изменения в конфигурацию для заданной параметром области сервисов realm")
    fun updateRealmConfiguration(
        @RequestHeader(name = "User-Agent", required = true) userAgent: String,
        @PathVariable("realm", required = true) realm: String,
        @RequestBody(required = true) representation: RealmRepresentation
    ): ResponseEntity<Any>


    /**
     * Безвозвратно удаляет заданный параметром realm, если он существует
     *
     * @param realm название области сервисов
     * @return статус выполнения и сообщение
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Realm deleted successfully", content = [Content()]),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name",
                content = [Content()]
            ),
            ApiResponse(responseCode = "404", description = "Not found preassigned realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @DeleteMapping("/{realm}")
    @Operation(summary = "Безвозвратно удаляет realm")
    fun deleteRealm(
        @RequestHeader(name = "User-Agent", required = true) userAgent: String,
        @PathVariable("realm", required = true) realm: String
    ): ResponseEntity<Any>


    /**
     * Выполняет чтение сущностей всех групп и подгрупп в области сервисов realm
     *
     * @param realm название области сервисов
     * @return статус выполнения, список сущностей groups или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm group's list received successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm name",
                content = [Content()]
            ),
            ApiResponse(responseCode = "404", description = "Not found preassigned realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/groups")
    @Operation(summary = "Выполняет чтение сущностей всех групп и подгрупп в области сервисов realm")
    fun getAllRealmGroups(
        @PathVariable("realm", required = true) realm: String
    ): ResponseEntity<Any>


    /**
     * Выполняет создание или обновление сущностей всех групп и подгрупп в области сервисов realm
     *
     * @param realm название области сервисов
     * @param importGroupList список групп с включенными подгруппами
     * @return статус выполнения, список сущностей groups или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm group's list updated successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm or group list",
                content = [Content()]
            ),
            ApiResponse(responseCode = "404", description = "Not found preassigned realm", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/groups")
    @Operation(summary = "Выполняет создание или обновление сущностей всех групп и подгрупп в области сервисов realm")
    fun createOrUpdateAllRealmGroups(
        @PathVariable("realm", required = true) realm: String,
        @RequestBody(required = true) importGroupList: List<GroupRepresentation>
    ): ResponseEntity<Any>


    /**
     * Выполняет чтение сущностей потока аутентификации realm
     *
     * @param realm название области сервисов
     * @param alias название потока аутентификации
     * @return статус выполнения, список сущностей groups или сообщение об ошибке
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm authenticate flow received successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm or flow name",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found preassigned realm or flow",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @GetMapping("/{realm}/authentication/flow")
    @Operation(summary = "Выполняет чтение сущностей потока аутентификации realm")
    fun getRealmAuthenticationFlow(
        @Parameter(description = "Название рабочей области")
        @PathVariable("realm", required = true) realm: String,
        @Parameter(description = "Название потока аутентификации")
        @RequestParam("alias", required = true) alias: String
    ): ResponseEntity<Any>


    /**
     * Выполняет создание нового потока аутентификации realm (копию переданного в параметрах)
     *
     * @param realm название области сервисов
     * @param importFlowDto импортируемый dto класс потока аутентификации
     * @return статус выполнения, список сущностей groups или сообщение об ошибке
     *
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm authenticate flow created successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm or flow",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found preassigned realm or flow",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/authentication/flow")
    @Operation(summary = "Выполняет создание копии потока аутентификации realm")
    fun createRealmAuthenticationFlow(
        @Parameter(description = "Название рабочей области")
        @PathVariable("realm", required = true) realm: String,
        @Parameter(description = "Импортная сущность потока аутентификации")
        @RequestBody(required = true) importFlowDto: ImportFlowDto
    ): ResponseEntity<Any>


    /**
     * Выполняет очистку кэша заданной параметром области сервисов
     *
     * @param realm название области сервисов
     * @return статус выполнения
     *
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Realm caches cleared successfully",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request parameter - realm is null or empty",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found preassigned realm in keycloak",
                content = [Content()]
            ),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/{realm}/clear/cache")
    @Operation(summary = "Выполняет создание копии потока аутентификации realm")
    fun clearKeycloakCache(
        @Parameter(description = "Название рабочей области")
        @PathVariable("realm", required = true) realm: String
    ): ResponseEntity<Any>

}