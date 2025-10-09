package vitos.local.keycloak_kotlin.models


data class MigrationRoleResponseDto(

    val created: MutableList<String> = mutableListOf(),
    val updated: MutableList<String> = mutableListOf()
) {

    fun finally() = created.size + updated.size
}
