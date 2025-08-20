package vitos.local.keycloak_kotlin.handlers

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.concurrent.TimeoutException


/**
 * Выполняет обработку кастомных исключений, для реализации бизнес логики
 * @author Vitalii Belotserkovskii, 18.04.2025
 */
@RestControllerAdvice
class GlobalExceptionHandler {


    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)


    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequestParametersException() {
        log.error("Bad parameters occurred in request")
    }


    @ExceptionHandler(TimeoutException::class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    fun handleTimeoutException() {
        log.error("Timeout occurred while getting delayed response")
    }


    @ExceptionHandler(IllegalAccessException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUnauthenticatedException() {
        log.error(">>>> Basic Authentication headers not found :: access DENIED")
    }


    @ExceptionHandler(AuthorizationDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleUnauthorizedException() {
        log.error(">>>> Basic Authentication headers found :: login|password permissions DENIED")
    }

}