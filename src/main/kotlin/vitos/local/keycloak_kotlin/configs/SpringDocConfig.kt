package vitos.local.keycloak_kotlin.configs

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "Keycloak 26.1.2 custom REST API",
        description = "Тестовый проект для проверки работы admin-client",
        version = "version 1.0",
        contact = Contact(name = "Belotserkovskii Vitalii", email = "vitaly@belo@gmail.com")
    )
)
class SpringdocConfig
