package vitos.local.keycloak_kotlin.migration.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.representations.idm.RoleRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants
import vitos.local.keycloak_kotlin.migration.models.JsonType
import vitos.local.keycloak_kotlin.migration.models.MigrateExchange
import vitos.local.keycloak_kotlin.migration.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.migration.models.MigrationResponseDto
import vitos.local.keycloak_kotlin.migration.services.keycloak.KeycloakRolesService


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

    companion object: Log()


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

                    val foundRealmRoles = realmResource.roles()
                        .list(0, Integer.MAX_VALUE, false)
                        ?.filter { it.description.isNullOrEmpty() || !it.description.startsWith("$") }
                        ?.toList() ?: emptyList()

                    if (foundRealmRoles.isNotEmpty()) {

                        val jsonAsString = objectMapper.writeValueAsString(foundRealmRoles)
                        val migrateRecord = MigrateExchange(realm, JsonType.REALM_ROLES, jsonAsString)
                        migrateRepository.save(migrateRecord)

                        logger.info("Successfully found Realm Roles count = ${foundRealmRoles.size}")
                        return ResponseEntity(foundRealmRoles, HttpStatus.OK)
                    }
                    logger.infoM("Realm roles fot found for [$realm]")
                    return ResponseEntity("NOT Found roles in realm = $realm",HttpStatus.NOT_FOUND)
                }
                logger.infoM("Realm [$realm] resource not found")
                return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.NOT_FOUND)

            } catch (ex: Exception) {
                return migrateService.writeErrorLoggerWithTextAndStatus(
                    ex, "Unknown error occurred during export realm roles"
                )
            }
        }
        logger.infoM("Invalid parameter realm name = [$realm]")
        return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
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

        if (realm.isEmpty() || realmRoles.isEmpty()) {
            logger.info("Invalid parameter realm name = [$realm], realmRoles size = [${realmRoles.size}]")
            return ResponseEntity(Constants.INVALID_REALM_OR_REALM_ROLES, HttpStatus.BAD_REQUEST)
        }

        logger.infoM("Procedure importing of Realm Roles for [$realm] started")
        try {
            migrateService.getRealmResource(realm)?.let { realmResource ->

                val responseDto = MigrationResponseDto()
                val foundRoles = realmResource.roles()
                    .list(0, Integer.MAX_VALUE, false)
                    ?.associateBy { it.name } ?: emptyMap()

                realmRoles.forEach { roleRepresentation ->

                    val roleName = roleRepresentation.name
                    if (foundRoles.containsKey(roleName)) {

                        // роль существует, необходимо обновить
                        val found = foundRoles[roleName]!!
                        val updated = keycloakRolesService
                            .updateRealmRoles(roleRepresentation, found, realmResource)
                        if (updated != null) {
                            logger.infoM("Realm Roles [$roleName] successfully updated")
                            responseDto.addUpdated(roleName)
                        }
                    } else {
                        // роль не существует, создаем новую
                        val created = keycloakRolesService.createRealmRole(roleRepresentation, realmResource)
                        if (created != null) {
                            logger.infoM("Realm Roles [$roleName] successfully created")
                            responseDto.addCreated(roleName)
                        }
                    }
                    responseDto.addFailedConditional(roleName)
                }
                logger.infoM("Successfully added Realm Roles count = ${responseDto.totalCount}")
                return ResponseEntity(responseDto, HttpStatus.OK)
            }
            logger.infoM("Realm [$realm] resource not found")
            return ResponseEntity(Constants.INVALID_REALM_NOT_FOUND, HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(
                ex, "Unknown error occurred during import realm roles"
            )
        }
    }

}

