package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.RealmRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NAME


/**
 * Сервисный слой для обеспечения методов миграции Realm Configuration
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateRealmService(

    private val keycloak: Keycloak,
    private val migrateService: MigrateCommonService,
    private val migrateRepository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateRealmService::class.java)
    }


    /**
     * Выполняет чтение настроек области сервисов realm
     *
     * @param realmName название области сервисов
     * @return статус выполнения, сущность настроек или сообщение об ошибке
     */
    fun getRealmConfiguration(realmName: String): ResponseEntity<Any> {

        if (realmName.isNotEmpty()) {

            try {
                migrateService.getRealmResource(realmName)?.let { realmResource ->

                    val configuration = realmResource.toRepresentation()

                    val jsonAsString = objectMapper.writeValueAsString(configuration)
                    val migrateRecord = MigrateExchange(realmName, JsonType.REALM_CONFIG, jsonAsString)
                    migrateRepository.save(migrateRecord)

                    logger.info("getRealmConfiguration() :: Successfully received realm \"$realmName\" configuration")
                    return ResponseEntity(configuration, HttpStatus.OK)
                }
                return ResponseEntity("Realm \"$realmName\" not exist or unavailable",HttpStatus.NOT_FOUND)

            } catch (ex: Exception) {
                logger.error("getRealmConfiguration() :: failed to get realm configuration for $realmName", ex)
                return migrateService.writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        return ResponseEntity(INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
    }


    /**
     * Выполняет создание новой или изменение существующей области сервисов realm.
     * В начале метод проверяет существование realm в заданной области сервисов
     *
     * @param realmName название области сервисов
     * @param realmRepresentation сущность новых настроек для области
     * @return статус выполнения, сущность настроек или сообщение об ошибке
     */
    fun updateRealmConfiguration(
        realmName: String,
        realmRepresentation: RealmRepresentation
    ): ResponseEntity<Any> {

        if (realmName.isNotEmpty()) {

            try {
                val realmResource = migrateService.getRealmResource(realmName)
                if (realmResource == null) {
                    // создаем новую область сервисов realm
                    realmRepresentation.id = null
                    realmRepresentation.realm = realmName
                    realmRepresentation.defaultRole.id = null
                    realmRepresentation.defaultRole.containerId = null
                    keycloak.realms().create(realmRepresentation)
                    logger.info("updateRealmConfiguration() :: Successfully created realm \"$realmName\" configuration")
                } else {
                    // обновляем область сервисов
                    val foundRealm = realmResource.toRepresentation()
                    realmRepresentation.id = foundRealm.id
                    realmRepresentation.realm = foundRealm.realm
                    realmRepresentation.defaultRole?.id = foundRealm.defaultRole?.id
                    realmRepresentation.defaultRole?.containerId = foundRealm.defaultRole?.containerId
                    realmResource.update(realmRepresentation)
                    logger.info("updateRealmConfiguration() :: Successfully updated realm \"$realmName\" configuration")
                }
                return ResponseEntity(realmRepresentation, HttpStatus.OK)

            } catch (ex: Exception) {
                logger.error("Failed to update Realm configuration for \"$realmName\"", ex)
                return migrateService.writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        return ResponseEntity(INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
    }

}

