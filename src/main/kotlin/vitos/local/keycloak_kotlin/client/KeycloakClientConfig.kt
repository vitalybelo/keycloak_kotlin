package vitos.local.keycloak_kotlin.client

import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient

/**
 * Конфигурация создания рест клиента для Keycloak API кастомного метода branch migration
 * При создании в builder привязывается метод получения/обновления токена для выполнения админ запроса в Keycloak.
 * @author Belotserkovskii Vitaly (c) 07.05.2026
 */
@Configuration
class KeycloakClientConfig(

    @Qualifier("keycloakRealm") private val keycloak: Keycloak,
    @Value($$"${keycloak.server.url}") private val baseKeycloakUrl: String
) {

    @Bean
    fun keycloakBranchClient(): KeycloakBranchClient {

        val restClient = RestClient.builder()
            .baseUrl(baseKeycloakUrl)
            .defaultStatusHandler({ it.isError }, { _, _ -> })
            .requestInterceptor { request, body, execution ->

                val token = keycloak.tokenManager().accessTokenString
                request.headers.setBearerAuth(token)
                execution.execute(request, body)
            }.build()

        val adapter = RestClientAdapter.create(restClient)
        val factory = HttpServiceProxyFactory.builderFor(adapter).build()

        return factory.createClient<KeycloakBranchClient>()
    }

}