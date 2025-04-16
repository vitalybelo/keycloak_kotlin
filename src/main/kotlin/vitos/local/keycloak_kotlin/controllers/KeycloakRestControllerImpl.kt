package vitos.local.keycloak_kotlin.controllers

import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import io.swagger.v3.oas.annotations.tags.Tag
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import vitos.local.keycloak_kotlin.interfaces.KeycloakRestController
import vitos.local.keycloak_kotlin.services.ExperimentsRestService
import vitos.local.keycloak_kotlin.services.KeycloakService
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

@Tag(
    name = "KeycloakRestController",
    description = "API управления учётными данными пользователей и паролями"
)
@Controller
@CrossOrigin
class KeycloakRestControllerImpl(
    private val keycloakService: KeycloakService,
    private val experimentsService: ExperimentsRestService
) : KeycloakRestController {

    private val log = LoggerFactory.getLogger(KeycloakRestControllerImpl::class.java)


    override fun changeUserPassword(
        @RequestParam("user", required = false) userName: String?,
        @RequestParam("password", required = false, defaultValue = "1") password: String?,
        @RequestHeader headers: Map<String, String>,
    ): ResponseEntity<Any> {
        return keycloakService.changeUserPassword(userName, password, headers)
    }


    override fun createKeycloakUser(@RequestBody(required = true) user: UserRepresentation?): ResponseEntity<Any> {

        if (user != null && user.username.isNotEmpty()) {
            log.info(">>>> Creating user {}", user.username)
            return keycloakService.createKeycloakUser(user)
        }
        log.info(">>>> Invalid create user parameters")
        return ResponseEntity("Invalid username", HttpStatus.BAD_REQUEST)
    }


    override fun getKeycloakWellKnown(): ResponseEntity<Any> {
        return keycloakService.getWellKnownEndPoints()
    }

    override fun getUserRepresentation(authentication: Authentication): ResponseEntity<Any> {
        return keycloakService.getUserRepresentation(authentication)
    }

    override fun getExtendedUserRepresentation(): ResponseEntity<Any> {
        return keycloakService.getExtendedUserRepresentation()
    }

    override fun getExtendedUserRepresentationList(): ResponseEntity<Any> {
        return keycloakService.getExtendedUserRepresentationList()
    }


    /**
     * Задаем переменную timeout, которую в дальнейшем используем для Callable<> обертки
     */
    private val timeoutLimiter: TimeLimiter =
        TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(2500)).build())

    @GetMapping("/public/transactional/limited/{millis}")
    override fun getDelayedResponse(@PathVariable millis: Long): Callable<ResponseEntity<Any>> {

        return TimeLimiter.decorateFutureSupplier(timeoutLimiter) {
            CompletableFuture.supplyAsync {
                experimentsService.getDelayedResponseString(millis)
            }
        }
    }

}