package vitos.local.keycloak_kotlin.controllers

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import vitos.local.keycloak_kotlin.interfaces.MigrationRestController
import vitos.local.keycloak_kotlin.services.MigrateRestService


@RestController
class MigrationRestControllerImpl(

    private val migrateRestService: MigrateRestService
) : MigrationRestController {


    override fun getAllRealmClientScopes(realm: String?): ResponseEntity<Any> {
        return migrateRestService.getRealmClientScopes(realm)
    }

}