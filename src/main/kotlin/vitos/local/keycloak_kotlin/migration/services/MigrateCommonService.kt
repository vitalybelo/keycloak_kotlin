package vitos.local.keycloak_kotlin.migration.services

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.FATAL_ERROR
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NAME
import vitos.local.keycloak_kotlin.logging.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * Сервисный слой для обеспечения методов миграции
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateCommonService(

    private val keycloak: Keycloak
) {

    companion object: Log() {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy-HHmm")
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
            logger.infoM("Representation: ${realmRepresentation.realm} found successfully")
            return realmResource
        } catch (ex: Exception) {
            logger.errorM("Error getting realm resource :: message = ${ex.message}")
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

        logger.errorM("Unexpected error occurred ${ex.message}, cause = ${ex.cause}", ex)
        return ResponseEntity(errorMessage, errorStatus)
    }

    /**
     * @return time stamp для копирования потоков и клиентов
     */
    fun getTimeStamp(): String = LocalDateTime.now().format(FORMATTER)


    /**
     * Выполняет очистку всех кэшей для заданно области сервисов
     *
     * @param realmName название рабочей области сервисов
     * @return код выполнения и сообщение
     */
    fun clearKeycloakCache(
        realmName: String
    ): ResponseEntity<Any> {

        getRealmResource(realmName)?.let { realmResource ->
            try {
                realmResource.clearRealmCache()
                realmResource.clearKeysCache()
                realmResource.clearCrlCache()
                realmResource.clearUserCache()

                logger.infoM("Successfully cleared keycloak cache for [$realmName]")
                return ResponseEntity("Cleared successfully", HttpStatus.OK)
            } catch (ex: Exception){
                logger.errorM("Failed to clear all caches for [$realmName]", ex)
                return writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        logger.infoM("Received realm name is invalid parameter = [$realmName[")
        return ResponseEntity(INVALID_REALM_NAME, HttpStatus.NOT_FOUND)
    }

}

