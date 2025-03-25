package vitos.local.keycloak_kotlin.controllers

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import vitos.local.keycloak_kotlin.authorization.AccessTokenService


@Controller
class MainPageController(

    private val request: HttpServletRequest,
    private val accessTokenService: AccessTokenService
) {

    private val logger: Logger = LoggerFactory.getLogger(MainPageController::class.java)

    companion object {
        const val NO_DETECTED = "No detected"
    }


    @GetMapping("/")
    fun index(authentication: Authentication, model: Model): String {

        val accessToken = accessTokenService.assign(authentication)
        val clientRoles = accessTokenService.streamClientRoles()
        val realmRoles = accessTokenService.streamRealmRoles()

        model.addAttribute("username", accessToken?.login ?: NO_DETECTED)
        model.addAttribute("first_name", accessToken?.firstName ?: NO_DETECTED)
        model.addAttribute("last_name", accessToken?.familyName ?: NO_DETECTED)
        model.addAttribute("phone", accessToken?.phone ?: NO_DETECTED)
        model.addAttribute("position", accessToken?.position ?: NO_DETECTED)
        model.addAttribute("client_roles", clientRoles.toString())
        model.addAttribute("realm_roles", realmRoles.toString())

        logger.info(model.toString())
        return "external"
    }

    @GetMapping("/logout")
    fun logout(): String {
        request.logout()
        return "redirect:/"
    }

    @GetMapping("/custom_logout")
    fun customLogout(): String {
        request.logout()
        return "redirect:/"
    }

}