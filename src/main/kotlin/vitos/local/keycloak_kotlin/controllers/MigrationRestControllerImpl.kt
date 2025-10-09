package vitos.local.keycloak_kotlin.controllers

import nl.basjes.parse.useragent.UserAgentAnalyzer
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import vitos.local.keycloak_kotlin.interfaces.MigrationRestController
import vitos.local.keycloak_kotlin.models.ClientExportDto
import vitos.local.keycloak_kotlin.services.MigrateClientScopeService
import vitos.local.keycloak_kotlin.services.MigrateClientsService
import vitos.local.keycloak_kotlin.services.MigrateRealmRolesService
import vitos.local.keycloak_kotlin.services.MigrateRealmService


@RestController
class MigrationRestControllerImpl(

    private val userAgentAnalyzer: UserAgentAnalyzer,
    private val migrateClientsService: MigrateClientsService,
    private val migrateClientScopeService: MigrateClientScopeService,
    private val migrateRealmRolesService: MigrateRealmRolesService,
    private val migrateRealmService: MigrateRealmService
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

    override fun getRealmClient(realm: String, clientId: String): ResponseEntity<Any> {
        return migrateClientsService.getRealmClients(realm, clientId)
    }

    override fun createOrUpdateRealmClient(
        realm: String,
        client: ClientExportDto
    ): ResponseEntity<Any> {
        return migrateClientsService.createOrUpdateRealmClient(realm, client)
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

    override fun getRealmConfiguration(
        userAgent: String,
        realm: String
    ): ResponseEntity<Any> {

        val user = userAgentAnalyzer.parse(userAgent)
        println("UserAgent header :: ${user.toJson()}")
        return migrateRealmService.getRealmConfiguration(realm)
    }

    override fun updateRealmConfiguration(
        userAgent: String,
        realm: String,
        representation: RealmRepresentation
    ): ResponseEntity<Any> {

        return migrateRealmService.updateRealmConfiguration(realm, representation)
    }

}