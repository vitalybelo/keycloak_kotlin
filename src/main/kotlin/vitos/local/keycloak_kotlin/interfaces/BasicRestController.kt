package vitos.local.keycloak_kotlin.interfaces

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping


@Tag(
    name = "BasicRestController",
    description = "API для тестирования features связанных с Basic авторизацией"
)
@RequestMapping("/basic")
interface BasicRestController {


    /**
     * Выполняет проверку авторизации входящего http запроса по протоколу Basic и тестирует метод
     * формирования заголовка авторизации для исходящих http запросов
     *
     * @param headers карта заголовков http запроса
     * @return сообщение и статус выполнения
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "GRANTED - запрос авторизирован", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "403", description = "Доступ не разрешен", content = [Content()])
        ]
    )
    @GetMapping("/auth")
    @Operation(summary = "Имитация запроса с Basic авторизацией в заголовке")
    fun receiveRequestBasicAuthorization(@RequestHeader headers: Map<String, String>?): ResponseEntity<Any>

}