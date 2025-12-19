package vitos.local.keycloak_kotlin.migration.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import vitos.local.keycloak_kotlin.logging.Log
import java.util.concurrent.atomic.AtomicInteger


/**
 * Дто ответа за запрос создания или обновления сущностей realm roles
 * @author Belotserkovskii Vitaly
 */
@JsonIgnoreProperties(ignoreUnknown = true, value = ["ignored"])
data class MigrationResponseDto(

    val created: MutableList<String> = mutableListOf(),
    val updated: MutableList<String> = mutableListOf(),
    val failed: MutableList<String> = mutableListOf(),
    var successCount: AtomicInteger = AtomicInteger(0),
    var updatedCount: AtomicInteger = AtomicInteger(0),
    var failedCount: AtomicInteger = AtomicInteger(0),
    var totalCount: AtomicInteger = AtomicInteger(0),
    var ignored: String? = "Ignored but showed"
) {

    companion object: Log()

    fun addCreated(name: String) {
        created.add(name)
        successCount.getAndIncrement()
        totalCount.getAndIncrement()
    }

    fun addUpdated(name: String) {
        updated.add(name)
        updatedCount.getAndIncrement()
        totalCount.getAndIncrement()
    }

    fun addFailedConditional(name: String) {
        if (!created.contains(name) && !updated.contains(name)) {
            failed.add(name)
            failedCount.getAndIncrement()
            totalCount.getAndIncrement()
        }
    }

    fun details(): String {
        return """/n
            ResponseDto:
            -----------------------------------------------------------
            created: $created
            updated: $updated
            failed: $failed
            successCount: ${successCount.get()}
            updatedCount: ${updatedCount.get()}
            failedCount: ${failedCount.get()}
            totalCount: ${totalCount.get()}
            -----------------------------------------------------------
        """.trimIndent()
    }
}
