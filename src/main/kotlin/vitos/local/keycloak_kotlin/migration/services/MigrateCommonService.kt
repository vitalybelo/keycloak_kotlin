package vitos.local.keycloak_kotlin.migration.services

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.RealmRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.constants.Constants
import vitos.local.keycloak_kotlin.logging.Log
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


/**
 * Сервисный слой для обеспечения методов миграции
 * @author Vitaly Belotserkovskii (c) 2025
 */
@Service
class MigrateCommonService(

    private val keycloak: Keycloak
) {

    var stamp: String = ""

    companion object: Log() {
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyy-HHmm")
    }


    /**
     * Выполняет проверку наличия области сервисов (realm) в Keycloak. Возвращает ресурс управления
     * областью сервисов только в том случае, если заданный realm в действительности существует.
     *
     * @param realmName название рабочей области сервисов
     * @return ресурс управления realm
     */
    fun getRealmResource(
        realmName: String
    ): RealmResource? {
        try {
            val realmResource = keycloak.realm(realmName)
            realmResource.toRepresentation()?.let {
                logger.infoM("Representation: [${it.realm}] found and ready to use")
            }
            return realmResource
        } catch (ex: Exception) {
            logger.errorM(
                "Error getting realm resource for realm = [$realmName]:: message = ${ex.message}, cause = ${ex.cause}"
            )
        }
        return null
    }


    /**
     * Возвращает "глубоко проверенную" сущность настроек области сервисов
     *
     * @param realmName название рабочей области сервисов
     * @return импортную сущность области сервисов или null
     */
    fun getExportRealmRepresentation(
        realmName: String,
    ): RealmRepresentation? {

        keycloak.realms().findAll().firstOrNull { it.realm == realmName }?.let {

            val realmResource = keycloak.realm(realmName)
            val representation =
                realmResource.partialExport(true, false)
            logger.infoM("Representation: ${representation.realm} deep checked successfully")
            return representation
        }
        logger.errorM("Error getting realm representation for = [$realmName]")
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
        errorLogMessage: String? = null,
        errorMessage: String = Constants.FATAL_ERROR,
        errorStatus: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR
    ): ResponseEntity<Any> {

        logger.errorM(errorLogMessage ?: "Unknown fatal error occurred", ex)
        return ResponseEntity(errorMessage, errorStatus)
    }


    /**
     * Устанавливает модификатор изменения имени сервисов, потоков, шагов и конфигураций при миграции.
     * В запросе на создание сервиса или потоков может передаваться необязательный параметр stamp.
     * Это строка на основе которой, создается модифицированное новое название сервиса или потока.
     * Если в запросе не передан такой параметр, в качестве штампа используется дата и время.
     *
     * @param requestStamp параметр штампа из запроса (может быть null)
     */
    fun setNameModificationStamp(requestStamp: String?) {
        if (requestStamp.isNullOrEmpty()) {
            stamp = LocalDateTime.now().format(FORMATTER)
        } else {
            stamp = requestStamp
        }
    }


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
                logger.infoM("Successfully cleared keycloak realm cache for [$realmName]")
                return ResponseEntity("Cleared successfully", HttpStatus.OK)
            } catch (ex: Exception){
                logger.errorM("Failed to clear all caches for [$realmName]", ex)
                return writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        logger.infoM("Received realm name is invalid parameter = [$realmName[")
        return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.NOT_FOUND)
    }


    /**
     * Возвращает список строк из переданной параметров запроса строки мульти значений.
     * В строке перечислены названия сервисов или потоков, по которым нужно вернуть экспортные сущности
     *
     * @param multiValuedSting строка с именами сервисов, разделенные запятой
     */
    fun getStringNameList(multiValuedSting: String): List<String> {

        val list = multiValuedSting.split(",")
            .stream().map { it.trim() }.filter { it.isNotEmpty() }.toList() ?: emptyList()
        return list
    }

}

