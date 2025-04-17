package vitos.local.keycloak_kotlin.controllers

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RequestHeader
import vitos.local.keycloak_kotlin.interfaces.BasicRestController
import vitos.local.keycloak_kotlin.services.BasicRestService


@Controller
@CrossOrigin
class BasicRestControllerImpl(
    private val basicRestService: BasicRestService
) : BasicRestController {


    override fun receiveRequestBasicAuthorization(@RequestHeader headers: Map<String, String>?): ResponseEntity<Any> {

        return basicRestService.getBasicAuthorization(headers)
    }

}