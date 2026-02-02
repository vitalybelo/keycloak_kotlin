package vitos.local.keycloak_kotlin.configs

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import vitos.local.keycloak_kotlin.logging.Log

/**
 * Дополнительный класс конфигурации безопасности для Basic аутентификации запросов из PCR_CONNECT
 * @author Vitalii Belotserkovskii, 23.05.2025
 */
@Configuration
@EnableWebSecurity
class SecurityBasicConfiguration(

    @Value($$"${digital.ruble.pcrconnect.callback.login:callback_login}")
    private val callbackLogin: String,
    @Value($$"${digital.ruble.pcrconnect.callback.password:callback_password}")
    private val callbackPassword: String
) {

    companion object: Log()

    /**
     * Выбираем метод хеширования пароля в памяти
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    /**
     * Прописываем пользователя для аутентификации обратных вызовов от PCR-CONNECT
     * Создаем UserDetailsService, специфичный для Basic Auth
     */
    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        val user = User.withUsername(callbackLogin)
            .password(passwordEncoder.encode(callbackPassword))
            .build()
        logger.infoM(">>>> PCR_CONNECT Callback User >>>> $callbackLogin :: $callbackPassword activated")
        return InMemoryUserDetailsManager(user)
    }

    /**
     * Создаем AuthenticationManager, специфичный для Basic Auth
     */
    @Bean
    fun basicAuthenticationManager(userDetailsService: UserDetailsService): AuthenticationManager {
        val daoProvider = DaoAuthenticationProvider(userDetailsService)
        daoProvider.setPasswordEncoder(passwordEncoder())
        return ProviderManager(daoProvider)
    }

    /**
     * Настраиваем цепочку фильтров только для uris /api/v3/customer/callback
     * По этому (и только по этому) пути будет применяться исключительно аутентификация Basic
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun basicAuthFilterChain(
        http: HttpSecurity,
        basicAuthenticationManager: AuthenticationManager
    ): SecurityFilterChain {

        http
            .securityMatcher("/basic/**")
            .authorizeHttpRequests { request ->
                request.requestMatchers("/basic/**").authenticated()
            }
            .httpBasic(Customizer.withDefaults())
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .authenticationManager(basicAuthenticationManager)

        return http.build()
    }

}