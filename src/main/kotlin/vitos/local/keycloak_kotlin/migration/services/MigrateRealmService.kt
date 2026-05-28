package vitos.local.keycloak_kotlin.migration.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RolesRepresentation
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.migration.models.JsonType
import vitos.local.keycloak_kotlin.migration.models.MigrateExchange
import vitos.local.keycloak_kotlin.migration.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.migration.models.ClientScopeExportDto
import vitos.local.keycloak_kotlin.migration.models.RealmExportConditions
import vitos.local.keycloak_kotlin.migration.models.FlowImportDto
import vitos.local.keycloak_kotlin.migration.models.RealmImportConditions
import vitos.local.keycloak_kotlin.migration.models.RealmImportResponseDto
import vitos.local.keycloak_kotlin.migration.services.keycloak.KeycloakGroupService
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds


/**
 * Сервисный слой для обеспечения методов миграции Realm Configuration
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateRealmService(

    @Qualifier("keycloakMaster") private val keycloak: Keycloak,
    private val migrateService: MigrateCommonService,
    private val migrateRealmRolesService: MigrateRealmRolesService,
    private val migrateClientScopeService: MigrateClientScopeService,
    private val migrateGroupsService: MigrateGroupsService,
    private val migrateAuthFlowsService: MigrateAuthFlowsService,
    private val migrateRepository: MigrateExchangeRepository,
    private val keycloakGroupsService: KeycloakGroupService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object: Log() {
        private const val READY_WAIT_COROUTINES_TIMEOUT = 20_000L
        private const val READY_WAIT_LOOP_DELAY = 5_000L
    }


    /**
     * Выполняет чтение настроек области сервисов realm
     *
     * @param realmName название области сервисов
     * @param exportConditions условия формирования экспортной сущности настроек области сервисов
     * @return статус выполнения, сущность настроек или сообщение об ошибке
     */
    fun getRealmConfiguration(

        realmName: String,
        exportConditions: RealmExportConditions
    ): ResponseEntity<Any> {

        if (realmName.isEmpty()) {
            return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
        }
        try {
            migrateService.getRealmResource(realmName)?.let { realmResource ->

                val configuration =
                    realmResource.partialExport(true, false)

                if (configuration != null) {

                    cleanConditionalRealConfiguration(
                        configuration,
                        exportConditions
                    )
                    val jsonAsString = objectMapper.writeValueAsString(configuration)
                    val migrateRecord = MigrateExchange(realmName, JsonType.REALM_CONFIG, jsonAsString)
                    migrateRepository.save(migrateRecord)

                    logger.infoM("Successfully received configuration for realm [$realmName] ")
                    return ResponseEntity(configuration, HttpStatus.OK)
                }
            }
            logger.warn("Realm [$realmName] could not be found")
            return ResponseEntity("Realm [$realmName] not exist or unavailable",HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(
                ex, errorLogMessage = "Attempt to receive realm configuration [$realmName] failed"
            )
        }
    }


    /**
     * Выполняет создание новой или изменение существующей области сервисов realm.
     * В начале метод проверяет существование realm в заданной области сервисов
     *
     * @param realmName название области сервисов
     * @param realmRepresentation сущность новых настроек для области
     * @param importConditions условия импортирования области сервисов
     *
     * @return статус выполнения, сущность настроек или сообщение об ошибке
     */
    fun updateRealmConfiguration(

        realmName: String,
        realmRepresentation: RealmRepresentation,
        importConditions: RealmImportConditions
    ): ResponseEntity<Any> {

        if (realmName.isEmpty()) {
            return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
        }
        try {
            logger.info("Updating Realm configuration [$realmName] procedure started")

            val foundRealmRepresentation =
                migrateService.getExportRealmRepresentation(realmName)

            createOrUpdateRealm(
                realmName,
                realmRepresentation,
                foundRealmRepresentation,
                importConditions
            )
            val realmImportResponseDto = RealmImportResponseDto(representation = realmRepresentation)
            partialRealmMigration(
                realmName,
                importConditions,
                realmImportResponseDto
            )
            return ResponseEntity(realmImportResponseDto, HttpStatus.OK)

        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(
                ex, errorLogMessage = "Failed to update Realm configuration [$realmName]"
            )
        }
    }


    /**
     * Выполняет частичную дополнительную миграцию roles, client scopes, groups, authentication flows
     *
     * @param realmName название рабочей области сервисов
     * @param migrateConditions условия выполнения миграции настроек области сервисов
     * @param migrationResponse сущность ответа по миграции настроек области сервисов
     */
    private fun partialRealmMigration(

        realmName: String,
        migrateConditions: RealmImportConditions,
        migrationResponse: RealmImportResponseDto
    ) {
        // давайте узнаем, нужен ли вообще частичный импорт
        if (!migrateConditions.isPartialNeed()) {
            logger.infoM("Partial import Realm configuration [$realmName] is not necessary")
            return
        }
        try {
            // Значит так, здесь самое гиблое место, мы попали сюда потому что хотим вместе с основными
            // настройками realm перенести roles, scopes, groups, flows. Сразу после создания realm, она
            // еще долго не появляется в списке на запрос client admin api. Ниже процедура, которая пингует
            // запросами api, чтобы узнать доступен уже ресурс управления областью. Как правило, сразу
            // после создания, он не доступен долго. Если мы обновляем область и попутно хотим затащить
            // указанные выше ресурсы (roles, scopes, groups, flows), задержек нет.
            // isCreatedRealmResourceReady - циклится с заданной задержкой и тайм-аутом
            val representation = isCreatedRealmResourceReady(realmName)
            if (representation != null) {

                migrationResponse.representation = representation
                if (migrateConditions.isMigrateRealmRoles) {
                    updateRealmRoles(realmName, migrateConditions.roles)
                    migrationResponse.rolesCount = migrateConditions.roles?.realm?.size ?: 0
                }
                if (migrateConditions.isMigrateClientScopes) {
                    updateClientScopes(realmName, migrateConditions.clientScopes)
                    migrationResponse.scopesCount = migrateConditions.clientScopes.clientScopes?.size ?: 0
                }
                if (migrateConditions.isMigrateRealmGroups) {
                    updateGroups(realmName, migrateConditions.groups)
                    assignDefaultGroups(realmName, migrateConditions)
                    migrationResponse.groupsCount = migrateConditions.groups?.size ?: 0
                }
                if (migrateConditions.isMigrateFlows) {
                    updateAuthenticationFlows(realmName, migrateConditions.importFlowsDto)
                    migrationResponse.flowsCount = migrateConditions.importFlowsDto.authenticationFlows?.size ?: 0
                }
            } else {
                logger.infoM("Procedure partial updating realm [$realmName] cannot receive realm admin resource")
            }
        } catch (ex: Exception) {
            logger.errorM("Partial realm import [$realmName] crashed by [${ex.message} || ${ex.cause}]", ex)
        }
    }


    /**
     * Выполняет добавление дефолтных групп в область сервисов. На этапе создания или обновления области
     * сервисов невозможно сразу добавить дефолтные группы, если их на данный момент нет в realm. Поэтому,
     * мы пробуем добавить дефолтные группы сразу после того, как добавили группы в рабочую область.
     *
     * @param realmName название рабочей области сервисов
     * @param importConditions условия импорта рабочей области
     */
    fun assignDefaultGroups(
        realmName: String,
        importConditions: RealmImportConditions
    ) {
        migrateService.getRealmResource(realmName)?.let { realmResource ->
            importConditions.defaultGroups?.let { defaultGroups ->
                defaultGroups.forEach { path ->
                    keycloakGroupsService.findGroupByPath(path, realmResource)?.let { group ->
                        realmResource.addDefaultGroup(group.id)
                    }
                }
            }
        }
    }


    /**
     * @return true если только что созданная область готова к продолжению миграции
     */
    private fun isCreatedRealmResourceReady(
        realmName: String,
    ): RealmRepresentation? {

        var representation: RealmRepresentation? = null
        try {
            runBlocking {
                representation = checkRealmResourceReady(realmName)
                if (representation != null) {
                    logger.infoM("""
                        |
                        | Received Realm Configuration for [$realmName] = ${representation.realm}:
                        | -------------------------------------------------------------------------
                        | realm name: ${representation.realm}
                        | groups count = ${representation.groups?.size}
                        | scopes count = ${representation.clientScopes?.size}
                        | realm roles count = ${representation.roles?.realm?.size}
                        | authentication flows count = ${representation.authenticationFlows?.size} 
                        | authenticator configs count = ${representation.authenticatorConfig?.size}
                        |
                        """.trimIndent()
                    )
                } else {
                    logger.infoM("Impossible to receive just created Realm configuration for [$realmName]")
                }
            }
        } catch (ex: Exception) {
            logger.errorM("Error getting of just created Realm Resource for [$realmName]", ex)
        }
        return representation
    }


    /**
     * Выполняет проверку доступности ресурса области к использованию
     * @param realmName название области сервисов
     * @return сущность настроек области или null
     */
    suspend fun checkRealmResourceReady(
        realmName: String
    ): RealmRepresentation? {

        val representation = AtomicReference<RealmRepresentation?>(null)
        val isSuccess = withTimeoutOrNull(READY_WAIT_COROUTINES_TIMEOUT.milliseconds) {
            do {
                representation.set(migrateService.getExportRealmRepresentation(realmName))
                if (representation.get() != null) break
                delay(READY_WAIT_LOOP_DELAY.milliseconds)
            } while (true)
            true
        } ?: false
        if (isSuccess) {
            return representation.get()
        }
        return null
    }


    /**
     * Выполняет манипуляции с id при создании или обновлении рабочей области сервисов
     *
     * @param realmName название области сервисов
     * @param importedRepresentation новая сущность области сервисов
     * @param foundRepresentation существующая сущность области сервисов или null
     * @param migrateConditions условия импортирования области сервисов
     */
    private fun createOrUpdateRealm(

        realmName: String,
        importedRepresentation: RealmRepresentation,
        foundRepresentation: RealmRepresentation?,
        migrateConditions: RealmImportConditions
    ) {

        // обнуляем информацию о потоках
        cleanAssignedFlowNames(importedRepresentation)
        // подготавливаем компоненты рабочей области
        updateComponents(importedRepresentation, foundRepresentation)
        // делаем дубликаты roles, groups, scopes, flows and conditionals и обнуляем
        migrateConditions.copyAndClear(importedRepresentation)

        try {
            if (foundRepresentation == null) {
                // создания новой рабочей области
                importedRepresentation.id = null
                importedRepresentation.realm = realmName
                keycloak.realms().create(importedRepresentation)
                logger.infoM("Successfully created realm [$realmName] configuration")

            } else {
                // обновление существующей рабочей области
                importedRepresentation.id = foundRepresentation.id
                importedRepresentation.realm = foundRepresentation.realm
                migrateService.getRealmResource(realmName)?.let { realmResource ->

                    realmResource.update(importedRepresentation)
                    logger.infoM("Successfully updated realm [$realmName] configuration")
                }
            }


        } catch (ex: Exception) {
            logger.errorM("Failed to create | update Realm configuration for [$realmName]", ex)
        }
    }


    /**
     * Обнуляет названия назначенных по умолчанию потоков аутентификации
     * @param importedRepresentation новая сущность области сервисов
     */
    private fun cleanAssignedFlowNames(
        importedRepresentation: RealmRepresentation,
    ) {
        importedRepresentation.browserFlow = null
        importedRepresentation.registrationFlow = null
        importedRepresentation.directGrantFlow = null
        importedRepresentation.resetCredentialsFlow = null
        importedRepresentation.clientAuthenticationFlow = null
        importedRepresentation.dockerAuthenticationFlow = null
        importedRepresentation.firstBrokerLoginFlow = null
    }


    /**
     * Выполняет обновление потоков аутентификации для импортируемой рабочей области сервисов
     *
     * @param realmName название области сервисов
     * @param flowImportDto список потоков и конфигураций
     */
    private fun updateAuthenticationFlows(
        realmName: String,
        flowImportDto: FlowImportDto?,
    ) {
        if (flowImportDto?.isAuthenticationFlowsPartialImport() == true) {
            migrateAuthFlowsService.createRealmAuthenticationFlow(realmName, null, flowImportDto)
            logger.infoM("Partial migration Flows and Configurations for [$realmName] is finished")
        } else {
            logger.infoM("Flows and Configurations not found in [$realmName] for partial import")
        }
    }

    /**
     * Выполняет обновление групп рабочей области сервисов, если в переданной сущности они есть
     *
     * @param realmName название области сервисов
     * @param importedGroups список импортируемых групп
     */
    private fun updateGroups(
        realmName: String,
        importedGroups: List<GroupRepresentation>?,
    ) {
        if (!importedGroups.isNullOrEmpty()) {
            migrateGroupsService.createOrUpdateAllRealmGroups(realmName, importedGroups)
            logger.infoM("Partial migration Groups for [$realmName] is finished")
        } else {
            logger.infoM("Groups not found in [$realmName] for partial import")
        }
    }


    /**
     * Выполняет обновление client scopes в рабочей области сервисов, если в переданной сущности они есть
     *
     * @param realmName название области сервисов
     * @param importedClientScopes импортированные client scopes
     */
    private fun updateClientScopes(
        realmName: String,
        importedClientScopes: ClientScopeExportDto?
    ) {
        val isPartialImpossible = importedClientScopes?.isClientScopesImportPossible() ?: false

        if (importedClientScopes != null && isPartialImpossible) {
            migrateClientScopeService.updateAllRealmClientScopes(realmName, importedClientScopes)
            logger.infoM("Partial migration Client Scopes for [$realmName] is finished")
        } else {
            logger.infoM("Client Scopes not found in [$realmName] for partial import")
        }
    }


    /**
     * Выполняет обновление ролей области сервисов, если в переданной сущности они есть
     *
     * @param realmName название области сервисов
     * @param rolesRepresentation сущность импортированных ролей
     */
    private fun updateRealmRoles(
        realmName: String,
        rolesRepresentation: RolesRepresentation?
    ) {
        val importedRealmRoles = rolesRepresentation?.realm

        if (!importedRealmRoles.isNullOrEmpty()) {
            migrateRealmRolesService.createOrUpdateRealmRoles(realmName, importedRealmRoles)
            logger.infoM("Partial migration Realm Roles for [$realmName] finished")
        } else {
            logger.infoM("Realm Roles not found in [$realmName] for partial import")
        }
    }


    /**
     * Создает новые или перезаписывает существующие компоненты в настройках области сервисов
     * При создании новой области - нужно обнулить все id в импортных сущностях компонентов. То
     * же самое мы делаем если в существующей области вообще нет провайдеров с компонентами.
     * Когда в обновляемой области существует похожий провайдер с найдем набором компонентов,
     * мы будем проверять существование конкретного компонента, и если не найдем, тогда id = null.
     * Если в существующей области мы найдем конкретный провайдер - подставим его id в импортный.
     *
     * @param importedRepresentation новая сущность области сервисов
     * @param foundRepresentation существующая сущность области сервисов или null
     */
    private fun updateComponents(

        importedRepresentation: RealmRepresentation,
        foundRepresentation: RealmRepresentation?
    ) {
        val importedComponents = importedRepresentation.components
        if (importedComponents.isNullOrEmpty()) return
        val foundComponents = foundRepresentation?.components

        if (foundRepresentation == null || foundComponents.isNullOrEmpty()) {
            importedComponents.forEach { component ->
                component.value?.forEach { it.id = null }
            }
            logger.infoM("All imported components ids cleared successfully for new realm")
        } else {
            importedComponents.forEach { importedProvider ->

                val foundComponentList = foundComponents[importedProvider.key]
                importedProvider.value.forEach { importedComponent ->

                    val importedProviderId = importedComponent.providerId!!
                    val foundComponentId = foundComponentList
                        ?.firstOrNull { component -> component.providerId == importedProviderId }?.id

                    if (foundComponentId != null) {
                        importedComponent.id = foundComponentId
                        logger.infoM("Component [$importedProviderId] update with id = $foundComponentId successfully")
                    } else {
                        importedComponent.id = null
                        logger.infoM("Component [$importedProviderId] not found, and would be created freshly")
                    }
                }
            }
        }
    }


    /**
     * Выполняет безвозвратное удаление realm, заданного параметром, если он существует
     * @param realmName название рабочей области
     * @return статус выполнения и сообщение
     */
    fun deleteRealm(
        realmName: String
    ): ResponseEntity<Any> {

        if (!realmName.isEmpty()) {
            val realmResource = migrateService.getRealmResource(realmName)
            if (realmResource != null) {
                try {
                    realmResource.remove()
                    logger.infoM("Realm [$realmName] removed successfully")
                    return ResponseEntity("Realm $realmName deleted successfully", HttpStatus.OK)

                } catch (ex: Exception) {
                    return migrateService.writeErrorLoggerWithTextAndStatus(ex, "Failed to remove $realmName")
                }
            }
            return ResponseEntity(Constants.INVALID_REALM_NOT_FOUND, HttpStatus.NOT_FOUND)
        }
        return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
    }


    /**
     * Вычищает из экспортной сущности ненужные параметры. Признаки попадания в экспортную сущность определенных
     * данных задается параметрами запроса. Параметры не обязательные, и если они не заданы, будет сформирована
     * максимально облегченная экспортная сущность.
     */
    private fun cleanConditionalRealConfiguration(

        realmRepresentation: RealmRepresentation,
        migrationConditions: RealmExportConditions
    ) {
        if (!migrationConditions.isMigrateRealmRoles) {
            realmRepresentation.roles = null
        }
        if (!migrationConditions.isMigrateClientScopes) {
            realmRepresentation.clientScopes = null
            realmRepresentation.defaultDefaultClientScopes = null
            realmRepresentation.defaultOptionalClientScopes = null
        }
        if (!migrationConditions.isMigrateRealmGroups) {
            realmRepresentation.groups = null
        }
        if (!migrationConditions.isMigrateFlows) {
            realmRepresentation.authenticationFlows = null
            realmRepresentation.authenticatorConfig = null
        }
    }

}

