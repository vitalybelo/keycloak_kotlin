package vitos.local.keycloak_kotlin.controllers

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import vitos.local.keycloak_kotlin.interfaces.PublicRestController
import vitos.local.keycloak_kotlin.services.PublicRestService

@Controller
@CrossOrigin
class PublicRestControllerImpl(
    private val publicRestService: PublicRestService,
) : PublicRestController {


    override fun getKeycloakWellKnown(): ResponseEntity<Any> {
        return publicRestService.getWellKnownEndPoints()
    }

}