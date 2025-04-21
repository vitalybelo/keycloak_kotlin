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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import java.util.concurrent.Callable

@Tag(
    name = "ExperimentsRestController",
    description = "API для проверки и тестирования features"
)
@RequestMapping("/experiments")
interface ExperimentsRestController {

    /**
     * Метод эмулирует блокирующий синхронный запрос с устанавливаемым значением timeout.
     * Если запрос обрабатывается за время меньшее чем установленный таймаут, возвращается 200 и ответ
     * @return строку сообщения и статус
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Выполнено успешно", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "408", description = "Таймаут в процессе выполнения запроса", content = [Content()])
        ]
    )
    @GetMapping("/timeout/{millis}")
    @Operation(summary = "Эмулирует синхронный блокирующий запрос с установленным параметром timeout")
    fun getDelayedResponse(@PathVariable millis: Long): Callable<ResponseEntity<Any>>


}