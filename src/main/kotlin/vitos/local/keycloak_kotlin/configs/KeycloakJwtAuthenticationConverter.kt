package vitos.local.keycloak_kotlin.configs

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.stream.Collectors


class KeycloakJwtAuthenticationConverter: Converter<Jwt, AbstractAuthenticationToken> {


    override fun convert(jwt: Jwt): AbstractAuthenticationToken {

        // извлечение ролей пользователя из токена
        val realmAccess: Map<String, Any> = jwt.getClaim("realm_access")
        val roles = realmAccess.getOrDefault("roles", emptyList<Any>()) as Collection<String>

        // преобразование ролей в GrantedAuthority
        val authorities: Collection<GrantedAuthority> = roles.stream()
            .map { role: String -> SimpleGrantedAuthority("ROLE_$role") }
            .collect(Collectors.toList())

        // создание объекта аутентификации
        return JwtAuthenticationToken(jwt, authorities)
    }

}