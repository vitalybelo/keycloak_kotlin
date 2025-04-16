package vitos.local.keycloak_kotlin.interfaces

import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/basic")
interface BasicRestController {


    @GetMapping("/auth")
    @Operation(summary = "Имитация запроса с Basic авторизацией в заголовке")
    fun receiveRequestBasicAuthorization(@RequestHeader headers: Map<String, String>?): ResponseEntity<Any>

}