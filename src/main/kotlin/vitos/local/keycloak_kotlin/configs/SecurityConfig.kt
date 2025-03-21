package vitos.local.keycloak_kotlin.configs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain


@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityConfigFilterChain(http: HttpSecurity): SecurityFilterChain {

        // позволяет аутентификацию с фронта, например для swagger
        http.csrf { it.disable() }
            .cors { it.disable() }
            .oauth2Login(Customizer.withDefaults())
            .formLogin { it.disable() }

        // определяет стратегию создания сессий
        http.sessionManagement { session ->
            session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        }

        // определяет обработчик для logout
        http.logout {
            it.clearAuthentication(true)
            it.invalidateHttpSession(true)
            it.logoutSuccessUrl("/")
        }

        // определяет URI для открытых, закрытых jwt токеном и вообще недоступных end-points
        http.authorizeHttpRequests { request ->
            request
                .requestMatchers("/users/well-known").permitAll()
                .requestMatchers("/users/create").authenticated()
                .requestMatchers("/users/**").authenticated()
                .anyRequest().denyAll()
            }

        // настраивает стандартный декодер для jwt токенов
        http.oauth2ResourceServer { oauth2 -> oauth2.jwt(Customizer.withDefaults()) }

        return http.build()
    }

}