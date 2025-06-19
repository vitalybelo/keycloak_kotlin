package vitos.local.keycloak_kotlin.configs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import vitos.local.keycloak_kotlin.handlers.KeycloakLogoutHandler
import vitos.local.keycloak_kotlin.handlers.SuccessLoginHandler


@Configuration
@EnableWebSecurity
class SecurityOAuth2Configuration(

    private val keycloakLogoutHandler: KeycloakLogoutHandler,
    private val successLoginHandler: SuccessLoginHandler

) {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun securityConfigFilterChain(http: HttpSecurity): SecurityFilterChain {

        // позволяет аутентификацию с фронта, например для swagger,
        // устанавливаем обработчик успешной аутентификации
        http.csrf { it.disable() }
            .cors { it.disable() }
            .oauth2Login(Customizer.withDefaults())
            .oauth2Login {
                it.successHandler(successLoginHandler)
            }
            .formLogin { it.disable() }

        // определяет стратегию создания сессий, по умолчанию "если потребуется"
        http.sessionManagement { session ->
            session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        }

        // определяет обработчик для logout и дополнительные действий
        http.logout {
            it.addLogoutHandler(keycloakLogoutHandler)
            it.invalidateHttpSession(true)
            it.clearAuthentication(true)
            it.logoutSuccessUrl("/")
        }

        // определяет URI для открытых, закрытых jwt токеном и ролями, отсекает все остальные end-points
        http.authorizeHttpRequests { request ->
            request
                .requestMatchers(
                    "/public/**",
                    "/experiments/**").permitAll()
                .requestMatchers("/users/create").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/**").authenticated()
                .anyRequest().denyAll()
        }

        // настраивает кастомный декодер для jwt токенов, обогащенный ролями области как authority
        http.oauth2ResourceServer { oauth2 ->
            oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(KeycloakJwtConverter()) }
        }

        return http.build()
    }

}