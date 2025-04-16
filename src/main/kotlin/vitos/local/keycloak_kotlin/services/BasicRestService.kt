package vitos.local.keycloak_kotlin.services

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RequestHeader
import vitos.local.keycloak_kotlin.authorization.BasicAuthorizationService


@Service
class BasicRestService(
    private val basicAuthorizationService: BasicAuthorizationService
) {

    private val log = LoggerFactory.getLogger(BasicRestService::class.java)

    fun getBasicAuthorization(@RequestHeader headers: Map<String, String>?): ResponseEntity<Any> {

        if (basicAuthorizationService.isAuthorized(headers)) {
            log.info(">>>> Getting basic authorization :: $headers")
            val result1 = basicAuthorizationService.addBasicHeader(headers)
            log.info(">>>> Creating basic authorization result1 :: $result1")
            val result2 = basicAuthorizationService.addBasicHeader()
            log.info(">>>> Creating basic authorization result2 :: $result2")
            return ResponseEntity("GRANTED", HttpStatus.OK)
        }
        return ResponseEntity("Unauthorized request", HttpStatus.UNAUTHORIZED)
    }

}