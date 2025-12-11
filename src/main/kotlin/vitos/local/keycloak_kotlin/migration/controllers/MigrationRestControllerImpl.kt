package vitos.local.keycloak_kotlin.migration.controllers

import nl.basjes.parse.useragent.UserAgentAnalyzer
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.migration.models.ClientExportDto
import vitos.local.keycloak_kotlin.migration.models.ImportFlowDto
import vitos.local.keycloak_kotlin.migration.interfaces.MigrationRestController
import vitos.local.keycloak_kotlin.migration.models.ClientScopeExportDto
import vitos.local.keycloak_kotlin.migration.services.MigrateAuthFlowsService
import vitos.local.keycloak_kotlin.migration.services.MigrateClientScopeService
import vitos.local.keycloak_kotlin.migration.services.MigrateClientsService
import vitos.local.keycloak_kotlin.migration.services.MigrateCommonService
import vitos.local.keycloak_kotlin.migration.services.MigrateGroupsService
import vitos.local.keycloak_kotlin.migration.services.MigrateRealmRolesService
import vitos.local.keycloak_kotlin.migration.services.MigrateRealmService

@RestController
class MigrationRestControllerImpl(

    private val userAgentAnalyzer: UserAgentAnalyzer,
    private val migrateClientsService: MigrateClientsService,
    private val migrateClientScopeService: MigrateClientScopeService,
    private val migrateRealmRolesService: MigrateRealmRolesService,
    private val migrateRealmService: MigrateRealmService,
    private val migrateGroupsService: MigrateGroupsService,
    private val migrateAuthFlowsService: MigrateAuthFlowsService,
    private val migrateCommonService: MigrateCommonService

) : MigrationRestController {

    companion object: Log()

    override fun getAllRealmClientScopes(realm: String): ResponseEntity<Any> {
        return migrateClientScopeService.getRealmClientScopes(realm)
    }


    override fun updateAllRealmClientScopes(
        realm: String,
        clientScopes: ClientScopeExportDto
    ): ResponseEntity<Any> {
        return migrateClientScopeService.updateAllRealmClientScopes(realm, clientScopes)
    }


    override fun getRealmClient(realm: String, clientId: String
    ): ResponseEntity<Any> {
        return migrateClientsService.getRealmClients(realm, clientId)
    }


    override fun createOrUpdateRealmClient(
        isAlwaysCreate: Boolean,
        realm: String,
        client: ClientExportDto
    ): ResponseEntity<Any> {
        return migrateClientsService.createOrUpdateRealmClient(
            isAlwaysCreate,
            realm,
            client)
    }


    override fun getAllRealmRoles(realm: String
    ): ResponseEntity<Any> {
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
        val browser = user.getValue("AgentNameVersion")
        logger.infoM("UserAgent header browser info :: $browser")
        return migrateRealmService.getRealmConfiguration(realm)
    }


    override fun updateRealmConfiguration(
        userAgent: String,
        realm: String,
        representation: RealmRepresentation
    ): ResponseEntity<Any> {
        return migrateRealmService.updateRealmConfiguration(realm, representation)
    }

    override fun deleteRealm(
        userAgent: String, realm: String
    ): ResponseEntity<Any> {

        val user = userAgentAnalyzer.parse(userAgent)
        val browser = user.getValue("AgentNameVersion")
        logger.infoM("UserAgent header browser info :: $browser")
        return migrateRealmService.deleteRealm(realm)
    }

    override fun getAllRealmGroups(
        realm: String
    ): ResponseEntity<Any> {
        return migrateGroupsService.getAllRealmGroups(realm)
    }


    override fun createOrUpdateAllRealmGroups(
        realm: String,
        importGroupList: List<GroupRepresentation>
    ): ResponseEntity<Any> {
        return migrateGroupsService.createOrUpdateAllRealmGroups(realm, importGroupList)
    }


    override fun getRealmAuthenticationFlow(
        realm: String,
        alias: String
    ): ResponseEntity<Any> {
        return migrateAuthFlowsService.getRealmAuthenticationFlow(realm, alias)
    }

    override fun createRealmAuthenticationFlow(
        realm: String,
        importFlowDto: ImportFlowDto
    ): ResponseEntity<Any> {
        return migrateAuthFlowsService.createRealmAuthenticationFlow(realm, importFlowDto)
    }

    override fun clearKeycloakCache(realm: String): ResponseEntity<Any> {
        return migrateCommonService.clearKeycloakCache(realm)
    }

}