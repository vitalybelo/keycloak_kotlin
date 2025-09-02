package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import vitos.local.keycloak_kotlin.constants.Constants.Companion.FATAL_ERROR
import vitos.local.keycloak_kotlin.models.OpenIdConfiguration


@Service
class PublicRestService(

    @param:Value("\${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private val issuerURL: String? = null,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

) {

    private val logger = LoggerFactory.getLogger(KeycloakRestService::class.java)

    /**
     * Возвращает информацию о конечных точках сервера авторизации Keycloak
     * @return json ответа конечной точки /.well-known/openid-configuration
     */
    fun getWellKnownEndPoints(): ResponseEntity<Any> {

        val configUrl = "$issuerURL/.well-known/openid-configuration"
        try {
            val response = restTemplate.getForEntity(configUrl, Any::class.java)
            if (response.statusCode.is2xxSuccessful && response.body != null) {
                val value = objectMapper.writeValueAsString(response.body)
                val result: OpenIdConfiguration = objectMapper.readValue(value)
                return ResponseEntity(result, HttpStatus.OK)
            }
        } catch (e: Exception) {
            logger.info(">>>> Ошибка чтения конфигурации области сервисов >>>> {}", e.message)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }

}