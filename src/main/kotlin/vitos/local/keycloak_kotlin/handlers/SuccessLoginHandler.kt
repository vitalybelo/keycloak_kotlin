package vitos.local.keycloak_kotlin.handlers

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class SuccessLoginHandler : SavedRequestAwareAuthenticationSuccessHandler() {

    private val log = LoggerFactory.getLogger(SuccessLoginHandler::class.java)

    /**
     * Вызов метода происходит только в случе успешной аутентификации пользователя на фронте.
     * Сначала зачитывается значения максимального времени простоя для текущего пользователя.
     * Если у пользователя не установлено значение, берем дефолтное значение из realm settings.
     *
     * @param request        http сервлет запроса
     * @param response       http сервлет ответа
     * @param authentication авторизационный класс spring security
     *
     * @throws jakarta.servlet.ServletException - для super.onAuthenticationSuccess
     * @throws java.io.IOException - для на super.onAuthenticationSuccess
     */
    @Throws(IOException::class, ServletException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse?,
        authentication: Authentication
    ) {
        log.info(">>>> SuccessLoginHandler :: Authentication success")
        super.onAuthenticationSuccess(request, response, authentication)
    }

}