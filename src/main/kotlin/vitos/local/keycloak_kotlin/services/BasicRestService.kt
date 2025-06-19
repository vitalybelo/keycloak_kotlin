package vitos.local.keycloak_kotlin.services

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service


@Service
class BasicRestService(
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
        return ResponseEntity("GRANTED", HttpStatus.OK)
    }

}