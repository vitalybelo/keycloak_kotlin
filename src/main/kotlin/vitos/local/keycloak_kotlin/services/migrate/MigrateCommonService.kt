package vitos.local.keycloak_kotlin.services.migrate

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants.Companion.FATAL_ERROR
import vitos.local.keycloak_kotlin.logging.Log


/**
 * Сервисный слой для обеспечения методов миграции
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateCommonService(

    private val keycloak: Keycloak
) {

    companion object: Log()


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
            logger.debugM("Representation: ${realmRepresentation.realm} found successfully")
            return realmResource
        } catch (ex: Exception) {
            logger.errorM("Error getting realm resource ${ex.message}", ex)
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
}

