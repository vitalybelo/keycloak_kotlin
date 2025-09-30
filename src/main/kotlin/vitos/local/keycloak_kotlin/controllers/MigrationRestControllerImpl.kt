package vitos.local.keycloak_kotlin.controllers

import org.keycloak.representations.idm.ClientScopeRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import vitos.local.keycloak_kotlin.interfaces.MigrationRestController
import vitos.local.keycloak_kotlin.services.MigrateClientScopeService


@RestController
class MigrationRestControllerImpl(

    private val migrateClientScopeService: MigrateClientScopeService
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

}