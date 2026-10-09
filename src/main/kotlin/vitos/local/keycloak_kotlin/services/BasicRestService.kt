package vitos.local.keycloak_kotlin.services

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.logging.Log


@Service
class BasicRestService {

    companion object: Log()


    /**
     * Выполняет проверку авторизации входящего http запроса по протоколу Basic и тестирует метод
     * формирования заголовка авторизации для исходящих http запросов
     *
     * @param headers карта заголовков http запроса
     * @return сообщение и статус выполнения
     */
    fun getBasicAuthorization(headers: Map<String, String>?): ResponseEntity<Any> {

        logger.infoM(">>>> Getting basic authorization :: ${headers?.toString()}")
        return ResponseEntity("ACCESS GRANTED", HttpStatus.OK)
    }

}