package vitos.local.keycloak_kotlin.services

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.authorization.BasicAuthorizationService


@Service
class BasicRestService(
    private val basicAuthorizationService: BasicAuthorizationService
) {

    private val log = LoggerFactory.getLogger(BasicRestService::class.java)


    /**
     * Выполняет проверку авторизации входящего http запроса по протоколу Basic и тестирует метод
     * формирования заголовка авторизации для исходящих http запросов
     *
     * @param headers карта заголовков http запроса
     * @return сообщение и статус выполнения
     */
    fun getBasicAuthorization(headers: Map<String, String>?): ResponseEntity<Any> {

        log.info(">>>> Getting basic authorization :: $headers")

        val result1 = basicAuthorizationService.addBasicHeader(headers)
        log.info(">>>> Creating basic authorization result1 :: $result1")

        val result2 = basicAuthorizationService.addBasicHeader()
        log.info(">>>> Creating basic authorization result2 :: $result2")

        return ResponseEntity("GRANTED", HttpStatus.OK)
    }

}