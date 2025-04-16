package vitos.local.keycloak_kotlin.controllers

import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import vitos.local.keycloak_kotlin.interfaces.ExperimentsRestController
import vitos.local.keycloak_kotlin.services.ExperimentsRestService
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture


@Controller
@CrossOrigin
class ExperimentsRestControllerImpl(
    private val experimentsService: ExperimentsRestService
): ExperimentsRestController {



    /**
     * Задаем переменную timeout, которую в дальнейшем используем для Callable<> обертки
     */
    private val timeoutLimiter: TimeLimiter =
        TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(2500)).build())

    override fun getDelayedResponse(@PathVariable millis: Long): Callable<ResponseEntity<Any>> {

        return TimeLimiter.decorateFutureSupplier(timeoutLimiter) {
            CompletableFuture.supplyAsync {
                experimentsService.getDelayedResponseString(millis)
            }
        }
    }

}