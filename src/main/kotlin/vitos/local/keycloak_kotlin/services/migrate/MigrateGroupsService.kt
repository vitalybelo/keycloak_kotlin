package vitos.local.keycloak_kotlin.services.migrate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import vitos.local.keycloak_kotlin.models.JsonType
import vitos.local.keycloak_kotlin.models.MigrateExchange
import vitos.local.keycloak_kotlin.repositories.MigrateExchangeRepository
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NAME
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_NOT_FOUND
import vitos.local.keycloak_kotlin.constants.Constants.Companion.INVALID_REALM_OR_GROUPS
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.models.MigrationResponseDto
import vitos.local.keycloak_kotlin.services.keycloak.KeycloakGroupService


/**
 * Сервисный слой для обеспечения методов миграции Groups
 * @author Vitaly Belotserkovskii
 */
@Service
class MigrateGroupsService(

    private val migrateService: MigrateCommonService,
    private val keycloakGroupService: KeycloakGroupService,
    private val migrateRepository: MigrateExchangeRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    companion object : Log()


    /**
     * Выполняет чтение сущностей всех групп в области сервисов realm
     *
     * @param realm название области сервисов
     * @return статус выполнения, список сущностей groups или сообщение об ошибке
     */
    fun getAllRealmGroups(realm: String): ResponseEntity<Any> {

        if (realm.isNotEmpty()) {
            try {
                migrateService.getRealmResource(realm)?.let { realmResource ->

                    val groupList = keycloakGroupService.collectGroups(realmResource)
                    if (groupList.isNotEmpty()) {

                        val jsonAsString = objectMapper.writeValueAsString(groupList)
                        val migrateRecord = MigrateExchange(realm, JsonType.GROUPS, jsonAsString)
                        migrateRepository.save(migrateRecord)

                        logger.infoM("In realm: $realm found: ${groupList.size} root groups")
                        return ResponseEntity(groupList, HttpStatus.OK)
                    }
                    logger.infoCM("In realm: $realm found no one group")
                    return ResponseEntity("Found no one groups in realm = $realm", HttpStatus.NOT_FOUND)
                }
                logger.warnM("Realm with $realm name not found")
                return ResponseEntity(INVALID_REALM_NOT_FOUND, HttpStatus.NOT_FOUND)
            } catch (ex: Exception) {
                logger.errorM("Error getting realm groups: ${ex.message}, cause = ${ex.cause}", ex)
                return migrateService.writeErrorLoggerWithTextAndStatus(ex)
            }
        }
        logger.warnM("Realm name is empty")
        return ResponseEntity(INVALID_REALM_NAME, HttpStatus.BAD_REQUEST)
    }


    /**
     * Выполняет создание или обновление сущностей всех групп и подгрупп в области сервисов realm
     *
     * @param realm название области сервисов
     * @param importGroupList список групп с включенными подгруппами
     * @return статус выполнения, список сущностей groups или сообщение об ошибке
     */
    fun createOrUpdateAllRealmGroups(
        realm: String,
        importGroupList: List<GroupRepresentation>): ResponseEntity<Any> {

        if (realm.isNotEmpty() && importGroupList.isNotEmpty()) {
            migrateService.getRealmResource(realm)?.let { realmResource ->
                try {
                    val foundGroups = realmResource.groups()
                            .groups("", 0, Integer.MAX_VALUE, false)
                            .associateBy { it.name }

                    migrateService.getRealmResource(realm)?.let { realmResource ->
                        val responseDto = MigrationResponseDto()
                        importGroupList.forEach { group ->
                            val groupName = group.name
                            if (foundGroups.containsKey(groupName)) {
                                // здесь обновляем корневую группу и обновляем дочерние группы
                                // TODO добавить метод обновления
                                responseDto.addUpdated(groupName)
                            } else {
                                // создаем новую корневую группу и далее создаем все дочерние
                                if (createRootGroupWithAssignRoles(group, realmResource)) {
                                    responseDto.addCreated(groupName)
                                }
                            }
                            responseDto.addFailedConditional(groupName)
                        }
                        return ResponseEntity(responseDto, HttpStatus.OK)
                    }
                } catch (ex: Exception) {
                    logger.error("Error getting realm groups: ${ex.message}", ex)
                }
            }
            logger.warnM("Realm with $realm name not found")
            return ResponseEntity(INVALID_REALM_NOT_FOUND, HttpStatus.NOT_FOUND)
        }
        logger.warnM("Realm name or import group list is empty")
        return ResponseEntity(INVALID_REALM_OR_GROUPS, HttpStatus.BAD_REQUEST)
    }


    /**
     * Выполняет создание корневой группы в области сервисов. После успешного создания, метод
     * вызывает функцию, которая выполняет присваивание realm ролей группе, а затем метод
     * присвоения client ролей (назначаются только существующие в области client роли).
     * Далее вызывается метод создания подгрупп по дереву вложений.
     *
     * @param groupRepresentation сущность создаваемой корневой группы
     * @param realmResource ресурс управления рабочей областью сервисов
     * @return true если группа создана успешно
     */
    private fun createRootGroupWithAssignRoles(
        groupRepresentation: GroupRepresentation,
        realmResource: RealmResource
    ): Boolean {
        try {
            groupRepresentation.id = null
            val response = realmResource.groups().add(groupRepresentation)

            if (response.status == HttpStatus.CREATED.value()) {
                // корневая группа создана успешно
                logger.infoM("Created group with name: ${groupRepresentation.name}")
                val id = CreatedResponseUtil.getCreatedId(response)
                val resource = realmResource.groups().group(id)
                keycloakGroupService.assignRealmRolesToGroup(groupRepresentation, realmResource, resource)
                keycloakGroupService.assignClientRolesToGroup(groupRepresentation, realmResource, resource)
                return true
            }
            logger.infoM("Created group with name: ${groupRepresentation.name} failed")

        } catch (ex: Exception) {
            logger.errorM("Error creating group: ${ex.message}", ex)
        }
        return false
    }




}

