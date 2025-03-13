package vitos.local.keycloak_kotlin.controllers

import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import vitos.local.keycloak_kotlin.services.KeycloakService

@Controller
@RequestMapping("/users")
class KeycloakRestController(private val keycloakService: KeycloakService) {

    private val log = LoggerFactory.getLogger(KeycloakRestController::class.java)


    @PostMapping("/change-password")
    fun changeUserPassword(@RequestParam("user") userName: String,
                           @RequestParam("password") password: String): ResponseEntity<String> {

        if (userName.isNotBlank() && password.isNotBlank()) {
            log.info(">>>> Changing password {}", userName)
            return keycloakService.changeUserPassword(userName, password)
        }
        invalidParametersLogging()
        return ResponseEntity("Invalid parameters", HttpStatus.BAD_REQUEST)
    }


    @PostMapping("/create")
    fun createKeycloakUser(@RequestBody user: UserRepresentation): ResponseEntity<Any> {

        if (!user.username.isNullOrEmpty()) {
            log.info(">>>> Creating user {}", user.username)
            return keycloakService.createKeycloakUser(user)
        }
        invalidParametersLogging()
        return ResponseEntity("Invalid username", HttpStatus.BAD_REQUEST)
    }


    private fun invalidParametersLogging() {
        log.info(">>>> Invalid change password parameters")
    }

}