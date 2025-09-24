package vitos.local.keycloak_kotlin.services

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service


@Service
class ExperimentsRestService {

    companion object {
        const val TIME_MILLIS: Long = 1000
    }


    /**
     * Инициализирует заданную параметром задержку и возвращает ответ, если обработка не была
     * прервана исключением, по которому существует отдельный обработчик
     *
     * @param millis задержка выполнения в мили-секундах
     * @return положительный ответ
     */
    fun getDelayedResponseString(millis: Long): ResponseEntity<Any> {

        try {
            Thread.sleep(millis)
        } catch (exception: InterruptedException) {
            return ResponseEntity(exception, HttpStatus.INTERNAL_SERVER_ERROR)
        }
        val float: Float = millis.toFloat() / TIME_MILLIS
        return ResponseEntity("Delayed at $float seconds HELLO", HttpStatus.OK)
    }


}