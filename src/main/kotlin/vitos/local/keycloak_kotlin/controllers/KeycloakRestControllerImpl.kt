package vitos.local.keycloak_kotlin.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import vitos.local.keycloak_kotlin.interfaces.KeycloakRestController
import vitos.local.keycloak_kotlin.services.KeycloakService

@Tag(
    name = "KeycloakRestController",
    description = "API управления учётными данными пользователей и паролями"
)
@Controller
@CrossOrigin
@RequestMapping("/users")
class KeycloakRestControllerImpl(private val keycloakService: KeycloakService) : KeycloakRestController {

    private val log = LoggerFactory.getLogger(KeycloakRestControllerImpl::class.java)


    override fun changeUserPassword(
        @RequestParam("user", required = false) userName: String?,
        @RequestParam("password", required = false, defaultValue = "1") password: String?,
        authentication: Authentication
    ): ResponseEntity<Any> {

        return keycloakService.changeUserPassword(userName, password, authentication)
    }


    override fun createKeycloakUser(@RequestBody user: UserRepresentation): ResponseEntity<Any> {

        if (!user.username.isNullOrEmpty()) {
            log.info(">>>> Creating user {}", user.username)
            return keycloakService.createKeycloakUser(user)
        }
        log.info(">>>> Invalid create user parameters")
        return ResponseEntity("Invalid username", HttpStatus.BAD_REQUEST)
    }


    override fun wellKnownKeycloak(): ResponseEntity<Any> {
        return keycloakService.getWellKnownEndPoints()
    }

}