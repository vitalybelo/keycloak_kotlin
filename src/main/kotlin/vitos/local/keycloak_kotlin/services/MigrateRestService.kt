package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository


@Service
class MigrateRestService(

    private val keycloak: Keycloak,
    private val repository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    private val logger = LoggerFactory.getLogger(MigrateRestService::class.java)


    /**
     * Выполняет чтение списка сущностей маппинга для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов для которой нужно выполнить чтение ClientScopes
     * @return статус выполнения и список сущностей, либо сообщение об ошибке
     */
    fun getRealmClientScopes(realm: String?): ResponseEntity<Any> {

        if (!realm.isNullOrEmpty()) {
            try {
                val clientScopes =
                    keycloak.realm(realm).clientScopes().findAll()
                if (!clientScopes.isNullOrEmpty()) {
                    val jsonAsString = objectMapper.writeValueAsString(clientScopes)
                    val migrateRecord = MigrateExchange(realm, JsonType.CLIENT_SCOPES, jsonAsString)
                    repository.save(migrateRecord)
                    return ResponseEntity(clientScopes, HttpStatus.OK)
                }
                return ResponseEntity("Found no one scopes in realm = $realm",HttpStatus.NOT_FOUND)
            } catch (ex: Exception) {
                logger.error("Unexpected error occurred ${ex.message}, cause = ${ex.cause}")
            }
            return ResponseEntity("Unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR)
        }
        return ResponseEntity("Invalid request parameter: realm name", HttpStatus.BAD_REQUEST)
    }

}

