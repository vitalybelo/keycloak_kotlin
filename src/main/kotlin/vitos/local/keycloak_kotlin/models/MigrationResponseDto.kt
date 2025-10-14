package vitos.local.keycloak_kotlin.models


/**
 * Дто ответа за запрос создания или обновления сущностей realm roles
 * @author Belotserkovskii Vitaly
 */
data class MigrationResponseDto(

    val created: MutableList<String> = mutableListOf(),
    val updated: MutableList<String> = mutableListOf(),
    val failed: MutableList<String> = mutableListOf(),
    var successCount: Int = 0,
    var updatedCount: Int = 0,
    var failedCount: Int = 0,
    var totalCount: Int = 0
) {

    fun addCreated(name: String) {
        created.add(name)
        ++successCount
        ++totalCount
    }

    fun addUpdated(name: String) {
        updated.add(name)
        ++updatedCount
        ++totalCount
    }

    fun addFailedConditional(name: String) {
        if (!created.contains(name) && !updated.contains(name)) {
            failed.add(name)
            ++failedCount
            ++totalCount
        }
    }
}
