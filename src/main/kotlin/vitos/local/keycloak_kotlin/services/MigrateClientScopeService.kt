package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository


/**
 * Сервисный слой для обеспечения методов миграции Client Scope
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateClientScopeService(

    private val keycloak: Keycloak,
    private val repository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateClientScopeService::class.java)
        private const val INVALID_REALM_NAME = "Invalid request parameter: realm name is empty"
    }

    /**
     * Выполняет чтение списка сущностей маппинга для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов для которой нужно выполнить чтение ClientScopes
     * @return статус выполнения и список сущностей, либо сообщение об ошибке
     */
    fun getRealmClientScopes(realm: String): ResponseEntity<Any> {

        if (realm.isNotEmpty()) {
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
                return writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        return ResponseEntity(INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
    }


    /**
     * Выполняет добавление маппинга в Client Scopes для заданной параметром области сервисов.
     * Список сущностей для маппинга метод получает как параметр, переданный в теле запроса.
     *
     * @param realm область сервисов, для которой требуется выполнить маппинг
     * @param clientScopes список сущностей маппинга Client Scopes
     * @return статус выполнения и отчет
     */
    fun updateAllRealmClientScopes(
        realm: String,
        clientScopes: List<ClientScopeRepresentation>
    ): ResponseEntity<Any> {

        if (realm.isEmpty()) {
            return ResponseEntity(INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
        }
        try {
            // получаем ресурс управления realm - если он реально существует или null
            val realmResource: RealmResource? = getRealmResource(realm)
            if (realmResource != null) {

                // получаем список названий существующих client scopes в целевом realm
                val foundScopeNames =
                    realmResource.clientScopes().findAll()?.map { it.name }?.toList() ?: emptyList()

                val addedSuccessfully = mutableListOf<String>()
                // вызываем метод создания client scope для каждого переданного в метод маппинга
                clientScopes.forEach { scope ->
                    if (!foundScopeNames.contains(scope.name)) {
                        if (createClientScopeWithMappers(scope, realmResource)) {
                            addedSuccessfully.add(scope.name)
                        }
                    }
                }
                return ResponseEntity(addedSuccessfully, HttpStatus.OK)
            }
        } catch (ex: Exception) {
            return writeErrorLoggerWithTextAndStatus(ex)
        }
        return ResponseEntity("Realm \"$realm\" not found",HttpStatus.NOT_FOUND)
    }


    /**
     * Выполняет создание Client Scope в заданной области сервисов. Если Client Scope содержит
     * protocol mappers, они тоже добавляются к вновь созданному Client Scope
     *
     * @param clientScope сущность создаваемого маппинга Client Scope
     * @param realmResource ресурс управления областью сервисов
     * @return true если выполнено успешно
     */
    private fun createClientScopeWithMappers(
        clientScope: ClientScopeRepresentation,
        realmResource: RealmResource
    ): Boolean {
        try {
            clientScope.id = null
            clientScope.protocolMappers?.forEach { it.id = null }
            val response = realmResource.clientScopes().create(clientScope)

            if (response.status == HttpStatus.CREATED.value()) {
                val scopeId = CreatedResponseUtil.getCreatedId(response)
                logger.debug("Created new client scope with name: ${clientScope.name}, id: $scopeId")
            }
            return true
        } catch (ex: Exception) {
            logger.error("Exception while creating client scope with protocolMappers: ${ex.message}, cause: ${ex.cause}, stackTrace: $ex")
        }
        return false
    }


    /**
     * Выполняет проверку наличия области сервисов (realm) в Keycloak. Возвращает ресурс управления
     * областью сервисов только в том случае, если заданный realm в действительности существует
     *
     * @param realmName название рабочей области сервисов
     * @return ресурс управления realm
     */
    private fun getRealmResource(realmName: String): RealmResource? {
        try {
            val realmResource = keycloak.realm(realmName)
            val realmRepresentation = realmResource.toRepresentation()
            logger.debug("Representation: {} found successfully", realmRepresentation.realm)
            return realmResource
        } catch (ex: Exception) {
            logger.error("getRealmResource() ${ex.message}, cause = ${ex.cause}")
        }
        return null
    }


    /**
     * Выводит error log ошибки и возвращает сущность http ответа с заданным текстом и статусом
     *
     * @param ex полученное при выполнении исключение
     * @param errorMessage сообщение для записи в тело ответа и log
     * @param errorStatus статус завершения запроса
     * @return сущность http ответа
     */
    private fun writeErrorLoggerWithTextAndStatus(
        ex: Exception,
        errorMessage: String = "Unexpected error occurred",
        errorStatus: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR
    ): ResponseEntity<Any> {

        logger.error("Unexpected error occurred ${ex.message}, cause = ${ex.cause}, stackTrace: ${ex.stackTrace}")
        return ResponseEntity(errorMessage, errorStatus)
    }
}

