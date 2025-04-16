package vitos.local.keycloak_kotlin.authorization

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.HashMap


@Service
@Suppress("unused")
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

        headers?.let { heads ->
            getIgnoreCaseHeaderAuthorization(heads)?.let { key ->

                val basicValue = headers[key]?.replace(BASIC_PREFIX, "")
                if (basicValue != null) {

                    val decoder = Base64.getDecoder()
                    val decodedString = String(decoder.decode(basicValue), StandardCharsets.UTF_8)
                    val chunks = decodedString.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (chunks.size == 2) {
                        log.info(">>>> Find Basic authorization as :: $decodedString")
                        return chunks[0] == callbackLogin && chunks[1] == callbackPassword
                    }
                }
            }
        }
        return false
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

        val encoder = Base64.getEncoder()
        val decodedString = "$connectLogin:$connectPassword"
        val encodedString = String(encoder.encode(decodedString.encodeToByteArray()))
        val authBasicString = BASIC_PREFIX + encodedString

        val resultMap: MutableMap<String, String> = HashMap(headers ?: emptyMap())
        resultMap[AUTHORIZATION_HEADER] = authBasicString
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

}