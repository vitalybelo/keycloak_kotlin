package vitos.local.keycloak_kotlin.controllers

import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import vitos.local.keycloak_kotlin.interfaces.KeycloakRestController
import vitos.local.keycloak_kotlin.services.KeycloakRestService

@Controller
@CrossOrigin
class KeycloakRestControllerImpl(

    private val keycloakService: KeycloakRestService,
) : KeycloakRestController {

    private val log = LoggerFactory.getLogger(KeycloakRestControllerImpl::class.java)


    override fun changeUserPassword(
        @RequestParam("user", required = false) userName: String?,
        @RequestParam("password", required = false, defaultValue = "1") password: String?,
        @RequestHeader headers: Map<String, String>,
    ): ResponseEntity<Any> {
        return keycloakService.changeUserPassword(userName, password, headers)
    }


    override fun createKeycloakUser(user: UserRepresentation?): ResponseEntity<Any> {

        if (user != null && user.username.isNotEmpty()) {
            log.info(">>>> Creating user {}", user.username)
            return keycloakService.createKeycloakUser(user)
        }
        log.info(">>>> Invalid create user parameters")
        return ResponseEntity("Invalid username", HttpStatus.BAD_REQUEST)
    }

    override fun getUserInfo(userId: String?, headers: Map<String, String>): ResponseEntity<Any> {
        return keycloakService.getUserInfo(userId, headers)
    }


    override fun getUserRepresentation(authentication: Authentication): ResponseEntity<Any> {
        return keycloakService.getUserRepresentation(authentication)
    }

    override fun getFullUserRepresentation(headers: Map<String, String>): ResponseEntity<Any> {
        return keycloakService.getFullUserRepresentation(headers)
    }


    override fun getExtendedUserRepresentation(): ResponseEntity<Any> {
        return keycloakService.getExtendedUserRepresentation()
    }


    override fun getExtendedUserRepresentationList(): ResponseEntity<Any> {
        return keycloakService.getExtendedUserRepresentationList()
    }


    override fun changeUserAttributes(
        key: String?,
        value: String?,
        attributesMap: Map<String, List<String>>?
    ): ResponseEntity<Any> {

        if (!key.isNullOrEmpty() && !value.isNullOrEmpty() && !attributesMap.isNullOrEmpty()) {
            return keycloakService.changeUserAttributes(key, value, attributesMap)
        }
        return ResponseEntity("Incorrect request parameters", HttpStatus.BAD_REQUEST)
    }


    override fun findGroupAssignedRoleList(headers: Map<String, String>?): ResponseEntity<Any> {
        return keycloakService.findGroupAssignedRoleList(headers)
    }

    override fun getUserListByAttribute(
        key: String?,
        value: String?
    ): ResponseEntity<Any> {
        return keycloakService.findUserListByAttributes(key, value)
    }

    override fun updateUserListByAttribute(
        userList: List<Map<String, Any>?>?
    ): ResponseEntity<Any> {
        return keycloakService.updateUserListByAttributes(userList)
    }

    override fun deleteUsersByAttributeList(
        key: String?,
        values: List<String?>?
    ): ResponseEntity<out Collection<String>> {
        return keycloakService.deleteUsersByAttributeList(key, values)
    }


}