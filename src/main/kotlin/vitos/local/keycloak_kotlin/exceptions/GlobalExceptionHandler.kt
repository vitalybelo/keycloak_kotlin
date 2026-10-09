package vitos.local.keycloak_kotlin.exceptions

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.models.ApiErrorResponse
import java.util.concurrent.TimeoutException


/**
 * Выполняет обработку кастомных исключений, для реализации бизнес логики
 * @author Vitalii Belotserkovskii, 18.04.2025
 */
@RestControllerAdvice
class GlobalExceptionHandler {


    companion object: Log()


    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequestParametersException() {
        logger.error("Bad parameters occurred in request")
    }


    @ExceptionHandler(TimeoutException::class)
    @ResponseStatus(HttpStatus.REQUEST_TIMEOUT)
    fun handleTimeoutException() {
        logger.error("Timeout occurred while getting delayed response")
    }


    @ExceptionHandler(IllegalAccessException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleUnauthenticatedException() {
        logger.error(">>>> Basic Authentication headers not found :: access DENIED")
    }


    @ExceptionHandler(AuthorizationDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleUnauthorizedException() {
        logger.error(">>>> Basic Authentication headers found :: login|password permissions DENIED")
    }


    @ExceptionHandler(InvalidUserRequestException::class)
    fun handleInvalidUserRequest(ex: InvalidUserRequestException): ResponseEntity<ApiErrorResponse> {

        logger.warn("Bad request intercepted: ${ex.message}")

        val errorResponse = ApiErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "Invalid parameters provided"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ApiErrorResponse> {

        val errorMessage = "Resource not found >>>> exception message = ${ex.message}"
        logger.warn(errorMessage)

        val errorResponse = ApiErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = ex.message ?: errorMessage
        )
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }

}



/**
 * Здесь кастомные классы исключений, используемые в бизнес логике
 * @author Vitalii Belotserkovskii, 18.04.2025
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)
class InvalidUserRequestException(message: String) : RuntimeException(message)
