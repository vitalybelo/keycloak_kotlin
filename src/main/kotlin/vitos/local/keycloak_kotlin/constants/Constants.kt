package vitos.local.keycloak_kotlin.constants

class Constants {

    companion object {
        const val FATAL_ERROR = "Unfortunately something went wrong"
        const val INVALID_REALM_NAME = "Invalid request parameter: realm name wrong or empty"
        const val INVALID_REALM_NOT_FOUND = "Invalid request parameter: realm not fount in Keycloak"
        const val INVALID_REALM_OR_GROUPS = "Invalid request parameter: realm or group list is empty"
        const val INVALID_REALM_OR_CLIENT_ID = "Invalid request parameter: realm or client_id is empty"
        const val INVALID_REALM_OR_REALM_ROLES = "Invalid request parameter: realm or realm role list is empty"
        const val INVALID_REALM_OR_FLOW_NAME = "Invalid request parameter: realm or flow names is empty"
        const val CREATE_ROLE_DESC = "Role created automatically during group migration"
        const val CREATE_CLIENT_DESC = "Client created automatically during group migration"
        const val CLIENT_NOT_CONFIGURED = "Import client representation not configured"
        const val INVALID_FLOW = "Invalid request parameter: flow dto incorrect"
    }

}