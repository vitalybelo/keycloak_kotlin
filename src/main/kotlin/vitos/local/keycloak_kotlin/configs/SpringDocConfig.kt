package vitos.local.keycloak_kotlin.configs

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "SWAGGER_NAME",
        description = "SWAGGER_DESCRIPTION",
        version = "SWAGGER_VERSION",
        contact = Contact(name = "SWAGGER_CONTACT_NAME", email = "SWAGGER_CONTACT_EMAIL")
    )
)
class SpringdocConfig
