package vitos.local.keycloak_kotlin.services.migrate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.representations.idm.RoleRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NAME
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_OR_REALM_ROLES
import vitos.local.keycloak_kotlin.models.MigrationResponseDto
import vitos.local.keycloak_kotlin.services.keycloak.KeycloakRolesService


/**
 * Сервисный слой для обеспечения методов миграции Realm Roles
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateRealmRolesService(

    private val migrateService: MigrateCommonService,
    private val keycloakRolesService: KeycloakRolesService,
    private val migrateRepository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateRealmRolesService::class.java)
    }

    /**
     * Выполняет чтение списка всех существующих realm ролей для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов для которой нужно выполнить чтение Realm Roles
     * @return статус выполнения, список сервисов Realm Roles - либо сообщение об ошибке
     */
    fun getRealmRoles(realm: String): ResponseEntity<Any> {

        if (realm.isNotEmpty()) {
            try {
                migrateService.getRealmResource(realm)?.let { realmResource ->

                    val realmRoleList = realmResource.roles()
                        .list(0, Integer.MAX_VALUE, false)
                        ?.filter { it.description.isNullOrEmpty()
                                || !it.description.startsWith("$") }
                        ?.toList() ?: emptyList()
                    if (realmRoleList.isNotEmpty()) {

                        val jsonAsString = objectMapper.writeValueAsString(realmRoleList)
                        val migrateRecord = MigrateExchange(realm, JsonType.REALM_ROLES, jsonAsString)
                        migrateRepository.save(migrateRecord)

                        logger.info("Successfully found Realm Roles count = ${realmRoleList.size}")
                        return ResponseEntity(realmRoleList, HttpStatus.OK)
                    }
                    return ResponseEntity("Found no one roles in realm = $realm",HttpStatus.NOT_FOUND)
                }
            } catch (ex: Exception) {
                return migrateService.writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        return ResponseEntity(INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
    }


    /**
     * Выполняет создание или обновление существующих realm ролей для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов
     * @param realmRoles список realm roles для создания или изменения
     * @return статус выполнения или сообщение об ошибке
     */
    fun createOrUpdateRealmRoles(
        realm: String,
        realmRoles: List<RoleRepresentation>
    ): ResponseEntity<Any> {

        if (realmRoles.isNotEmpty() && realm.isNotEmpty()) {
            try {
                val realmResource = migrateService.getRealmResource(realm)
                val responseDto = MigrationResponseDto()
                if (realmResource != null) {

                    val foundRoles = realmResource.roles()
                        .list(0, Integer.MAX_VALUE, false)
                        ?.associateBy { it.name } ?: emptyMap()

                    realmRoles.forEach { role ->
                        val roleName = role.name
                        if (foundRoles.containsKey(roleName)) {

                            // роль существует, необходимо обновить
                            val found = foundRoles[roleName]!!
                            val updated = keycloakRolesService
                                .updateRealmRoles(role, found, realmResource)

                            if (updated != null) {
                                responseDto.addUpdated(roleName)
                            }
                        } else {

                            // роль не существует, необходимо создать новую
                            val created = keycloakRolesService
                                .createRealmRole(role, realmResource)

                            if (created != null) {
                                responseDto.addCreated(roleName)
                            }
                        }
                        responseDto.addFailedConditional(roleName)
                    }
                    logger.info("Successfully added Realm Roles count = ${responseDto.totalCount}")
                    return ResponseEntity(responseDto, HttpStatus.OK)
                }
            } catch (ex: Exception) {
                return migrateService.writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        return ResponseEntity(INVALID_REALM_OR_REALM_ROLES, HttpStatus.BAD_REQUEST)
    }


}

