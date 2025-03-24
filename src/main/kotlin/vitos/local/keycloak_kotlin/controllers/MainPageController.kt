package vitos.local.keycloak_kotlin.controllers

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping


@Controller
class MainPageController(
    private val request: HttpServletRequest
) {

   private val logger: Logger = LoggerFactory.getLogger(MainPageController::class.java)

    @GetMapping("/")
    fun index(authentication: Authentication, model: Model): String {

        val user: OidcUser = authentication.principal as OidcUser

        model.addAttribute("username", user.getClaimAsString("preferred_username") ?: "NULL")
        model.addAttribute("phone", user.getClaimAsString("phone") ?: "NULL")
        model.addAttribute("position", user.getClaimAsString("position") ?: "NULL")
        model.addAttribute("roles", user.getClaimAsMap("realm_access"))

        logger.info(model.toString())
        return "external"
    }

    @GetMapping("/logout") // не задействована
    fun logout(): String {
        request.logout()
        return "redirect:/"
        //return "custom_logout";
    }

}