package vitos.local.keycloak_kotlin.controllers

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.keycloak.representations.idm.UserRepresentation
import vitos.local.keycloak_kotlin.controllers.interfaces.KeycloakRestController
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersRequestDto
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersResponseDto
import vitos.local.keycloak_kotlin.services.KeycloakRestService

@Controller
@CrossOrigin
class KeycloakRestControllerImpl(
    private val keycloakService: KeycloakRestService,
) : KeycloakRestController {

    companion object: Log()


    override fun changeUserPassword(
        userName: String,
        password: String,
        headers: Map<String, String>,
    ): ResponseEntity<Any> {
        return keycloakService.changeUserPassword(userName, password, headers)
    }

    override fun createKeycloakUser(user: UserRepresentation): ResponseEntity<Any> {
        logger.infoM(">>>> Received request to create user == ${user.username}")
        return keycloakService.createKeycloakUser(user)
    }

    override fun getUserInfo(userId: String?, headers: Map<String, String>): ResponseEntity<Any> {
        return keycloakService.getUserInfo(userId, headers)
    }

    override fun getUserRepresentation(authentication: Authentication): ResponseEntity<Any> {
        return keycloakService.getUserRepresentation(authentication)
    }

    override fun getEnrichedUserRepresentation(headers: Map<String, String>): ResponseEntity<Any> {
        return keycloakService.getEnrichedUserRepresentation(headers)
    }

    override fun getBruteForceUserRepresentation(): ResponseEntity<Any> {
        return keycloakService.getBruteForceUserRepresentation()
    }

    override fun getBruteForceUserRepresentationList(): ResponseEntity<Any> {
        return keycloakService.getBruteForceUserRepresentationList()
    }

    override fun getEffectiveUserRoles(): ResponseEntity<Any> {
        return keycloakService.getEffectiveUserRoles()
    }

    override fun changeUserAttributes(
        key: String,
        value: String,
        attributesMap: Map<String?, List<String>?>
    ): ResponseEntity<Any> {
       return keycloakService.changeUserAttributes(key, value, attributesMap)
    }

    override fun findUserGroupedRoleList(headers: Map<String, String>): ResponseEntity<Any> {
        return keycloakService.findUserGroupedRoleList(headers)
    }

    override fun getUserListByAttributeKeyValue(
        key: String,
        value: String
    ): ResponseEntity<Any> {
        return keycloakService.findUserListByAttributesKeyValue(key, value)
    }

    override fun deleteUsersByAttributeList(
        abscustIdValues: DeleteUsersRequestDto
    ): ResponseEntity<List<DeleteUsersResponseDto>> {

        val values = abscustIdValues.getValueSet()
        if (values.isEmpty()) return ResponseEntity(HttpStatus.BAD_REQUEST)

        logger.infoM(">>>> Received request to delete users of $values")
        return keycloakService
            .deleteUsersByAttributeList("abscustId", values, abscustIdValues.isHardDelete)
    }

    override fun manageUserBranchMigration(
        searchKey: String,
        searchValue: String,
        modifyKey: String,
        modifyValue: String
    ): ResponseEntity<Any> {
        return keycloakService.manageUserBranchMigration(searchKey, searchValue, modifyKey, modifyValue)
    }

    override fun getUserAttackDetection(userId: String?): ResponseEntity<Any> {
        return keycloakService.getUserAttackDetection(userId)
    }
}
