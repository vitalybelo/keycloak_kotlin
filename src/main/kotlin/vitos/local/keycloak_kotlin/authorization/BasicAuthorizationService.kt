package vitos.local.keycloak_kotlin.authorization

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.stereotype.Component
import sun.security.jgss.GSSUtil.login
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.HashMap


@Suppress("unused")
@Component("basicAuthorization")
class BasicAuthorizationService(

    @Value("\${digital.ruble.pcrconnect.login}")
    private val connectLogin: String? = null,
    @Value("\${digital.ruble.pcrconnect.password}")
    private val connectPassword: String? = null,
    @Value("\${digital.ruble.pcrconnect.callback.login}")
    private val callbackLogin: String? = null,
    @Value("\${digital.ruble.pcrconnect.callback.login}")
    private val callbackPassword: String? = null

) {

    companion object {
        private val log = LoggerFactory.getLogger(BasicAuthorizationService::class.java)
        const val AUTHORIZATION_HEADER: String = "Authorization"
        const val BASIC_PREFIX: String = "Basic "
    }


    /**
     * Выполняет проверку Basic авторизации http callback запроса от коробки PCR CONNECT.
     * @param headers карта заголовков запроса
     * @return true если запрос авторизированный
     */
    fun isAuthorized(headers: Map<String, String>?): Boolean {

        log.debug(">>>> Callback Basic Values: login = $callbackLogin,  password = $callbackPassword")
        if (callbackLogin == null ||  callbackPassword == null) {
            log.info(">>>> Configuration parameters of Basic authentication missed")
            return false
        }

        headers?.let { heads ->
            getIgnoreCaseHeaderAuthorization(heads)?.let { key ->

                val basicValue = headers[key]?.replace(BASIC_PREFIX, "")
                if (basicValue != null) {

                    val decodedString =
                        String(Base64.getDecoder().decode(basicValue), StandardCharsets.UTF_8)
                    val chunks = decodedString.split(":")

                    if (chunks.size == 2) {
                        if (chunks[0] == callbackLogin && chunks[1] == callbackPassword) {
                            log.info(">>>> Find Basic authorization received :: $decodedString :: access GRANTED")
                            return true
                        }
                        throw AuthorizationDeniedException("403")
                    }
                }
            }
        }
        throw IllegalAccessException("401")
    }


    /**
     * Формирует заголовок Basic авторизации для http запросов в PCR CONNECT коробку.
     * Если в качестве параметра передана не пустая карта заголовков, заголовок авторизации
     * добавляется к уже существующим. Если карта заголовков не передана или передана = null,
     * создается новая MutableMap<String, String> только с одним значением ключа Authorization
     *
     * @param headers заголовки (не обязательный)
     * @return headers с Basic авторизацией
     */
    fun addBasicHeader(headers: Map<String, String>? = null): Map<String, String> {

        val resultMap: MutableMap<String, String> = HashMap(headers ?: emptyMap())
        val authorizationKey = getIgnoreCaseHeaderAuthorization(resultMap)

        if (authorizationKey != null) {
            resultMap.remove(authorizationKey)
        }
        val basicHeader = getBasicValues()
        resultMap[basicHeader.first] = basicHeader.second
        return resultMap
    }


    /**
     * Выполняет поиск среди ключей карты http заголовков - заголовка Authorization без учета регистра
     *
     * @param headers - карта http заголовков
     * @return строку валидного ключа для извлечения или null
     */
    private fun getIgnoreCaseHeaderAuthorization(headers: Map<String, String>): String? {
        return headers.keys.stream()
            .filter { key -> key.equals(AUTHORIZATION_HEADER, true) }
            .findFirst().orElse(null)
    }


    /**
     * Возвращает пару из ключа заголовка авторизации и значения ключа - строки авторизации Basic
     */
    private fun getBasicValues(): Pair<String, String> {

        val username = connectLogin ?: ""
        val password = connectPassword ?: ""
        log.debug(">>>> Request Basic Values: login = \'$username\',  password = \'$password\'")

        val decodedString = "$username:$password"
        val encodedString = String(Base64.getEncoder().encode(decodedString.encodeToByteArray()))
        return Pair(AUTHORIZATION_HEADER, BASIC_PREFIX + encodedString)
    }
}