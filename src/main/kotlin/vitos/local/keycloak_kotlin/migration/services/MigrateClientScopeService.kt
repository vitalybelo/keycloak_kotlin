package vitos.local.keycloak_kotlin.migration.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.ClientScopeRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.migration.models.JsonType
import vitos.local.keycloak_kotlin.migration.models.MigrateExchange
import vitos.local.keycloak_kotlin.migration.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.migration.models.ClientScopeExportDto
import vitos.local.keycloak_kotlin.migration.models.MigrationResponseDto


/**
 * Сервисный слой для обеспечения методов миграции Client Scope
 * @author Vitaly Belotserkovskii (c) 2025
 */
@Service
class MigrateClientScopeService(

    private val migrateService: MigrateCommonService,
    private val migrateRepository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object: Log()


    /**
     * Выполняет чтение списка сущностей маппинга для заданной входным параметром области сервисов.
     *
     * @param realm название области сервисов для которой нужно выполнить чтение ClientScopes
     * @return статус выполнения и список сущностей, либо сообщение об ошибке
     */
    fun getRealmClientScopes(realm: String): ResponseEntity<Any> {

        if (realm.isEmpty()) {
            logger.warnM("Invalid realm name = [$realm]")
            return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
        }
        logger.infoM("Procedure exporting client scope for realm [$realm] started")
        try {
            migrateService.getRealmResource(realm)?.let { realmResource ->

                val scopeExportDto = ClientScopeExportDto()
                scopeExportDto.clientScopes = realmResource.clientScopes()?.findAll() ?: emptyList()
                scopeExportDto.default = realmResource.defaultDefaultClientScopes?.map { it.name }?.toList() ?: emptyList()
                scopeExportDto.optional = realmResource.defaultOptionalClientScopes?.map { it.name }?.toList() ?: emptyList()

                if (scopeExportDto.clientScopes!!.isNotEmpty()) {

                    val jsonAsString = objectMapper.writeValueAsString(scopeExportDto)
                    val migrateRecord = MigrateExchange(realm, JsonType.CLIENT_SCOPES, jsonAsString)
                    migrateRepository.save(migrateRecord)

                    logger.infoM("Client scope for realm [$realm] exported successfully")
                    return ResponseEntity(scopeExportDto, HttpStatus.OK)
                }
                logger.warnM("Not found client scopes in [$realm]")
                return ResponseEntity("Client scopes not found in [$realm]",HttpStatus.NOT_FOUND)
            }
            logger.warnM("Realm [$realm] resource not available")
            return ResponseEntity(Constants.INVALID_REALM_NOT_FOUND, HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(
                ex, "Unknown error during exporting client scopes in [$realm]"
            )
        }
    }


    /**
     * Выполняет добавление маппинга в Client Scopes для заданной параметром области сервисов.
     * Список сущностей для маппинга метод получает как параметр, переданный в теле запроса.
     *
     * @param realm область сервисов, для которой требуется выполнить маппинг
     * @param importedClientScopes список сущностей маппинга Client Scopes
     * @return статус выполнения и отчет
     */
    fun updateAllRealmClientScopes(

        realm: String,
        importedClientScopes: ClientScopeExportDto
    ): ResponseEntity<Any> {

        val clientScopes = importedClientScopes.clientScopes
        if (realm.isEmpty() || clientScopes.isNullOrEmpty()) {
            logger.warnM("Invalid parameters realm name = [$realm], client scopes size = ${clientScopes?.size}")
            return ResponseEntity(Constants.INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
        }
        logger.infoM("Procedure importing client scopes to [$realm] started")
        try {
            val responseDto = MigrationResponseDto()
            // получаем ресурс управления realm - если он реально существует или null
            migrateService.getRealmResource(realm)?.let { realmResource ->

                // получаем список названий существующих client scopes в целевом realm
                val foundScopes = realmResource.clientScopes().findAll()
                    ?.associateBy { it.name } ?: emptyMap()

                // вызываем метод создания client scope для каждого переданного в метод маппинга
                clientScopes.forEach { scope ->

                    val scopeName = scope.name
                    if (foundScopes.containsKey(scopeName)) {
                        // обновляем существующий scope
                        val found = foundScopes[scopeName]!!
                        val isUpdated = updateClientScopeWithMappers(
                            scope,
                            found,
                            importedClientScopes, realmResource
                        )
                        if (isUpdated) {
                            responseDto.addUpdated(scopeName)
                        }
                    } else {
                        // добавляем новый scope
                        val isCreated = createClientScopeWithMappers(
                            scope,
                            importedClientScopes, realmResource
                        )
                        if (isCreated) {
                            responseDto.addCreated(scopeName)
                        }
                    }
                    responseDto.addFailedConditional(scopeName)
                }
                logger.infoM("Client scope for realm [$realm] updated successfully")
                return ResponseEntity(responseDto, HttpStatus.OK)
            }
            logger.warnM("Realm [$realm] resource not available")
            return ResponseEntity(Constants.INVALID_REALM_NOT_FOUND,HttpStatus.NOT_FOUND)

        } catch (ex: Exception) {
            return migrateService.writeErrorLoggerWithTextAndStatus(
                ex, "Unknown error during importing client scopes in [$realm]"
            )
        }
    }


    /**
     * Выполняет создание Client Scope в заданной области сервисов. Если Client Scope содержит
     * protocol mappers, они тоже добавляются к вновь созданному Client Scope
     *
     * @param clientScope сущность создаваемого маппинга Client Scope
     * @param importedExportDto импортируемая сущность client scopes
     * @param realmResource ресурс управления областью сервисов
     * @return true если выполнено успешно
     */
    private fun createClientScopeWithMappers(

        clientScope: ClientScopeRepresentation,
        importedExportDto: ClientScopeExportDto,
        realmResource: RealmResource
    ): Boolean {

        var response: Response? = null
        try {
            clientScope.id = null
            clientScope.protocolMappers?.forEach { it.id = null }
            response = realmResource.clientScopes().create(clientScope)

            if (response.status == HttpStatus.CREATED.value()) {
                val scopeId = CreatedResponseUtil.getCreatedId(response)
                val scope = realmResource.clientScopes().get(scopeId).toRepresentation()

                logger.debug("Created new client scope with name: ${clientScope.name}, id: $scopeId")
                assignClientScope(scope, importedExportDto, realmResource)
                return true
            }
        } catch (ex: Exception) {
            logger.errorM("Creating client scope failed: ${ex.message}, cause: ${ex.cause}", ex)
        } finally {
            response?.close()
        }
        return false
    }


    /**
     * Выполняет назначение созданному или обновляемому client scope значение Default или Optional
     *
     * @param clientScope сущность создаваемого маппинга Client Scope
     * @param importedExportDto импортируемая сущность client scopes
     * @param realmResource ресурс управления областью сервисов
     */
    private fun assignClientScope(

        clientScope: ClientScopeRepresentation,
        importedExportDto: ClientScopeExportDto,
        realmResource: RealmResource
    ) {
        val id = clientScope.id
        val name = clientScope.name
        val foundDefaults = realmResource.defaultDefaultClientScopes.map { it.name }.toList()
        val foundOptionals = realmResource.defaultOptionalClientScopes.map { it.name }.toList()
        try {
            if (importedExportDto.default?.contains(name) == true) {
                // нужно назначить DEFAULT
                if (!foundDefaults.contains(name)) {
                    // продолжаем если сейчас не DEFAULT
                    if (foundOptionals.contains(name)) {
                        // если сейчас OPTIONAL - удаляем
                        logger.debug("Remove optional for scope [$name]")
                        realmResource.removeDefaultOptionalClientScope(id)
                    }
                    // теперь можно назначить DEFAULT
                    realmResource.addDefaultDefaultClientScope(id)
                    logger.debug("Default client scope added for [$name]")
                } else {
                    logger.debug("Default client scope already exists for [$name]")
                }
            } else if (importedExportDto.optional?.contains(name) == true) {
                // нужно назначить OPTIONAL
                if (!foundOptionals.contains(name)) {
                    // продолжаем если сейчас не OPTIONAL
                    if (foundDefaults.contains(name)) {
                        // если сейчас DEFAULT - удаляем
                        logger.debug("Remove default for scope [$name]")
                        realmResource.removeDefaultDefaultClientScope(id)
                    }
                    realmResource.addDefaultOptionalClientScope(id)
                    logger.debug("Optional client scope added for [$name]")
                } else {
                    logger.debug("Optional client scope already exists for [$name]")
                }
            }
        } catch (ex: Exception) {
            logger.errorM("Assign client scope [$name] failed: ${ex.message}", ex)
        }
    }


    /**
     * Выполняет обновление существующего Client Scope с набором ProtocolMappers.
     * Если добавляемый ProtocolMappers совпадает существующим, перезаписываем сохраняя старый id.
     *
     * @param clientScope импортная сущность нового Client Scope
     * @param foundClientScope сущность существующего Client Scope
     * @param importedExportDto импортируемая сущность Client Scope
     * @param realmResource ресурс управления областью сервисов
     * @return true если выполнено успешно
     *
     */
    private fun updateClientScopeWithMappers(
        clientScope: ClientScopeRepresentation,
        foundClientScope: ClientScopeRepresentation,
        importedExportDto: ClientScopeExportDto,
        realmResource: RealmResource

    ): Boolean {
        try {
            clientScope.id = foundClientScope.id
            val foundMappers =
                foundClientScope.protocolMappers?.associateBy { it.name } ?: emptyMap()

            clientScope.protocolMappers?.forEach { protocolMapper ->
                val name = protocolMapper.name
                if (foundMappers.containsKey(name)) {
                    protocolMapper.id = foundMappers[name]!!.id
                } else {
                    protocolMapper.id = null
                }
            }
            realmResource.clientScopes().get(clientScope.id)?.update(clientScope)
            assignClientScope(clientScope, importedExportDto, realmResource)
            logger.debug("Updated client scope with name: ${clientScope.name}, id: ${clientScope.id}")
            return true

        } catch (ex: Exception) {
            logger.error("updateClientScopeWithMappers() creating client scope failed: ${ex.message}, cause: ${ex.cause}", ex)
        }
        return false
    }
}

