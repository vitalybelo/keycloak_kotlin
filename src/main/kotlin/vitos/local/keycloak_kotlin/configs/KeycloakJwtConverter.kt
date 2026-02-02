package vitos.local.keycloak_kotlin.configs

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import vitos.local.keycloak_kotlin.logging.Log
import java.util.stream.Collectors


class KeycloakJwtConverter: Converter<Jwt, AbstractAuthenticationToken> {


    companion object: Log()


    /**
     * Метод конвертирует realm роли, которые назначены пользователю в список authorities для цепочки
     * фильтров конфигурации безопасности приложения
     */
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {

        var authorities: Collection<GrantedAuthority> = emptyList()
        try {
            // извлечение мульти-карты ролей области пользователя из токена
            val realmAccess: Map<String, MutableList<String>?>? = jwt.getClaim("realm_access")
            if (!realmAccess.isNullOrEmpty()) {
                // извлечение списка ролей области для пользователя
                val roles = realmAccess["roles"]

                if (!roles.isNullOrEmpty()) {
                    // преобразование ролей в GrantedAuthority
                    authorities = roles.stream()
                        .map { role: String -> SimpleGrantedAuthority("ROLE_$role") }
                        .collect(Collectors.toList())
                }
            }
        } catch (ex: Exception) {
            logger.errorM(">>>> Exception occurred while converting authorities of JWT, message = ${ex.message}, cause = ${ex.cause}")
        }
        // создание объекта аутентификации
        return JwtAuthenticationToken(jwt, authorities)
    }

}