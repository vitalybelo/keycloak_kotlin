package vitos.local.keycloak_kotlin.models

import org.keycloak.representations.idm.UserRepresentation


data class ApiUserRepresentationListResponse(
    val status: Int,
    val result: String,
    val description: String,
    val payload: List<UserRepresentation>
) {
    companion object {

        fun success(userList: List<UserRepresentation>): ApiUserRepresentationListResponse {
            val usernameList = userList.map { it.username }
            return ApiUserRepresentationListResponse(
                status = 200,
                result = "success",
                description = usernameList.joinToString(","),
                payload = userList
            )
        }
    }
}
