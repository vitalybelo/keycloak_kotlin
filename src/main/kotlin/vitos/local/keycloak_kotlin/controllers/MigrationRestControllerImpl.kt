package vitos.local.keycloak_kotlin.controllers

import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import vitos.local.keycloak_kotlin.interfaces.MigrationRestController
import vitos.local.keycloak_kotlin.services.MigrateClientScopeService
import vitos.local.keycloak_kotlin.services.MigrateClientsService
import vitos.local.keycloak_kotlin.services.MigrateRealmRolesService


@RestController
class MigrationRestControllerImpl(

    private val migrateClientsService: MigrateClientsService,
    private val migrateClientScopeService: MigrateClientScopeService,
    private val migrateRealmRolesService: MigrateRealmRolesService
) : MigrationRestController {


    override fun getAllRealmClientScopes(realm: String): ResponseEntity<Any> {
        return migrateClientScopeService.getRealmClientScopes(realm)
    }

    override fun updateAllRealmClientScopes(
        realm: String,
        clientScopes: List<ClientScopeRepresentation>
    ): ResponseEntity<Any> {
        return migrateClientScopeService.updateAllRealmClientScopes(realm, clientScopes)
    }

    override fun getAllRealmClients(realm: String, clientId: String): ResponseEntity<Any> {
        return migrateClientsService.getRealmClients(realm, clientId)
    }

    override fun getAllRealmRoles(realm: String): ResponseEntity<Any> {
        return migrateRealmRolesService.getRealmRoles(realm)
    }

    override fun createOrUpdateAllRealmRoles(
        realm: String,
        realmRoleList: List<RoleRepresentation>
    ): ResponseEntity<Any> {
        return migrateRealmRolesService.createOrUpdateRealmRoles(realm, realmRoleList)
    }

}