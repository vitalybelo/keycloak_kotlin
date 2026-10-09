package vitos.local.keycloak_kotlin.configs

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Активирует механизмы защиты методов. Параметры внутри нее включают разные стандарты аннотаций для проверки пра,
 * такие как @PreAuthorize, @PostAuthorize, @Secured("ROLE_ADMIN"), @RolesAllowed, @PermitAll, @DenyAll
 * @author Belotserkovskii Vitalii (c)
 */
@Configuration
@EnableMethodSecurity(
    prePostEnabled = true,
    securedEnabled = true,
    jsr250Enabled = true)
class MethodSecurityConfig