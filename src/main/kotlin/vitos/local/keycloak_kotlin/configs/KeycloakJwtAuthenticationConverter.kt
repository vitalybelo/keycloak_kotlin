package vitos.local.keycloak_kotlin.configs

import org.slf4j.LoggerFactory
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.stream.Collectors


class KeycloakJwtConverter: Converter<Jwt, AbstractAuthenticationToken> {


    private val log = LoggerFactory.getLogger(KeycloakJwtConverter::class.java)


    /**
     * Метод конвертирует realm роли, которые назначены пользователю в список authorities для цепочки
     * фильтров конфигурации безопасности приложения
     */
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {

        var authorities: Collection<GrantedAuthority> = emptyList()
        try {
            // извлечение мульти-карты ролей области пользователя из токена
            val realmAccess: Map<String, MutableList<String>> = jwt.getClaim("realm_access")
            if (realmAccess.isNotEmpty()) {

                // извлечение списка ролей области для пользователя
                val roles = realmAccess["roles"]

                if (!roles.isNullOrEmpty()) {
                    // преобразование ролей в GrantedAuthority
                    authorities = roles.stream()
                        .map { role: String -> SimpleGrantedAuthority("ROLE_$role") }
                        .collect(Collectors.toList())
                }
            }
        } catch (e: Exception) {
            log.error(">>>> Exception occurred while converting authorities of JWT\n{}", e.localizedMessage)
        }
        // создание объекта аутентификации
        return JwtAuthenticationToken(jwt, authorities)
    }

}