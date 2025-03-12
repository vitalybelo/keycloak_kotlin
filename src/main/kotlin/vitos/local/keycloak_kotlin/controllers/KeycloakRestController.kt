package vitos.local.keycloak_kotlin.controllers

import org.keycloak.representations.idm.UserRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import vitos.local.keycloak_kotlin.models.ChangeUserPasswordDto
import vitos.local.keycloak_kotlin.services.KeycloakService

@Controller
@RequestMapping("/users")
class KeycloakRestController(private val keycloakService: KeycloakService) {


    @PostMapping("/change-password")
    fun changeUserPassword(@RequestBody dto: ChangeUserPasswordDto): ResponseEntity<String> {
        if (dto.validate()) {
            return keycloakService.changeUserPassword(dto)
        }
        return ResponseEntity("Invalid parameters", HttpStatus.BAD_REQUEST)
    }

    @PostMapping("/create")
    fun createKeycloakUser(@RequestBody user: UserRepresentation): ResponseEntity<Any> {
        if (!user.username.isNullOrEmpty()) {
            return keycloakService.createKeycloakUser(user)
        }
        return ResponseEntity("Invalid username", HttpStatus.BAD_REQUEST)
    }

}