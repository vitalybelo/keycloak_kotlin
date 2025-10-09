package vitos.local.keycloak_kotlin.constants

class Constants {

    companion object {
        const val FATAL_ERROR = "Непредвиденная ошибка"
        const val INVALID_REALM_NAME = "Invalid request parameter: realm name wrong or empty"
        const val INVALID_REALM_OR_CLIENT_ID = "Invalid request parameter: realm or client_id is empty"
        const val INVALID_REALM_OR_REALM_ROLES = "Invalid request parameter: realm or realm role list is empty"
    }
}