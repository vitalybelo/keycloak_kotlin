package vitos.local.keycloak_kotlin.client

import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable

/**
 * Rest Client keycloak, вызывающий кастомный метод установки атрибута пользователю
 * @author Belotserkovskii Vitaly (c) 07.05.2026
 */
@HttpExchange("/admin/realms/{realm}/branch")
interface KeycloakBranchClient {

    @PostExchange("/users/migration")
    fun manageMigrationFlag(
        @PathVariable realm: String,

        @RequestParam("searchKey") searchKey: String,
        @RequestParam("searchValue") searchValue: String,
        @RequestParam("modifyKey") modifyKey: String,
        @RequestParam("modifyValue") modifyValue: String
    ): ResponseEntity<Void>
}