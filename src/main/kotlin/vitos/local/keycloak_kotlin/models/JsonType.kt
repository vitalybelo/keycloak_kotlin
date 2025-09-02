package vitos.local.keycloak_kotlin.models

enum class JsonType(
    val description: String
) {
    CLIENT_SCOPES("Client Scope Mapping"),
    CLIENT_ENTITY("Client Entity")
}