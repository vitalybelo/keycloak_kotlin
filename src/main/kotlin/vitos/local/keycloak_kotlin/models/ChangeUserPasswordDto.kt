package vitos.local.keycloak_kotlin.models

data class ChangeUserPasswordDto(
    var user: String? = null,
    var password: String? = null
) {

    fun validate() : Boolean {
        return !user.isNullOrBlank() && !password.isNullOrBlank()
    }
}