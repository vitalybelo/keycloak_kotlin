package vitos.local.keycloak_kotlin.services

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.FATAL_ERROR


/**
 * Сервисный слой для обеспечения методов миграции
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateCommonService(

    private val keycloak: Keycloak
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MigrateCommonService::class.java)
    }


    /**
     * Выполняет проверку наличия области сервисов (realm) в Keycloak. Возвращает ресурс управления
     * областью сервисов только в том случае, если заданный realm в действительности существует
     *
     * @param realmName название рабочей области сервисов
     * @return ресурс управления realm
     */
    fun getRealmResource(realmName: String): RealmResource? {
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
    fun writeErrorLoggerWithTextAndStatus(
        ex: Exception,
        errorMessage: String = FATAL_ERROR,
        errorStatus: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR
    ): ResponseEntity<Any> {

        logger.error("Unexpected error occurred ${ex.message}, cause = ${ex.cause}", ex)
        return ResponseEntity(errorMessage, errorStatus)
    }
}

