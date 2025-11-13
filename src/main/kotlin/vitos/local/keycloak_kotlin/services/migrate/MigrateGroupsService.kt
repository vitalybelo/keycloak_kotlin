package vitos.local.keycloak_kotlin.services.migrate

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.GroupResource
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
        importGroupList: List<GroupRepresentation>
    ): ResponseEntity<Any> {

        if (realm.isNotEmpty() && importGroupList.isNotEmpty()) {

            logger.infoM("Start creating new groups")
            migrateService.getRealmResource(realm)?.let { realmResource ->
                try {
                    realmResource.clearRealmCache()
                    val foundGroups = realmResource.groups()
                            .groups("", 0, Integer.MAX_VALUE, false)
                            .associateBy { it.name }

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
                    logger.infoM("Successfully created new groups: ${responseDto.details()}")
                    return ResponseEntity(responseDto, HttpStatus.OK)

                } catch (ex: Exception) {
                    logger.error("Error creating realm groups: ${ex.message}", ex)
                }
            }
            logger.warnM("Realm \"$realm\" not found")
            return ResponseEntity(INVALID_REALM_NOT_FOUND, HttpStatus.NOT_FOUND)
        }
        logger.warnM("Realm name and|or import group list is empty")
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
        var response: Response? = null
        val groupName = groupRepresentation.name
        try {
            groupRepresentation.id = null
            response = realmResource.groups().add(groupRepresentation)
            if (response.status == HttpStatus.CREATED.value()) {

                logger.infoM("Root group: $groupName created successfully")
                groupRepresentation.id = CreatedResponseUtil.getCreatedId(response)
                val resource = realmResource.groups().group(groupRepresentation.id)
                response.close()
                keycloakGroupService.assignRealmRolesToGroup(groupRepresentation, resource, realmResource)
                keycloakGroupService.assignClientRolesToGroup(groupRepresentation, resource, realmResource)
                createSubGroupWithAssignRoles(
                    groupRepresentation,
                    resource,
                    realmResource
                )
                return true
            }
            logger.infoM("Creating group with name: $groupName failed. Rest api ADD finished with not 201 code")

        } catch (ex: Exception) {
            logger.errorM("Error creating group: ${ex.message}", ex)
        } finally {
            response?.close()
        }
        response?.close()
        return false
    }


    /**
     * Выполняет создание подгрупп для родительской группы, метод рекурсивно гоняет по
     * всему дереву подгрупп, существующих от корневой группы и ниже.
     *
     * @param parentGroupRepresentation сущность родительской группы
     * @param parentGroupResource ресурс управления родительской группой
     * @param realmResource ресурс управления рабочей областью realm
     */
    private fun createSubGroupWithAssignRoles(

        parentGroupRepresentation: GroupRepresentation,
        parentGroupResource: GroupResource,
        realmResource: RealmResource
    ) {
        var response: Response? = null
        val groupName = parentGroupRepresentation.name
        val subGroupList: List<GroupRepresentation>? = parentGroupRepresentation.subGroups
        if (subGroupList.isNullOrEmpty()) {
            logger.infoM("Group: \"$groupName\" has no one sub groups")
            return
        }
        try {
            subGroupList.forEach { subGroup ->

                subGroup.id = null
                response = parentGroupResource.subGroup(subGroup)
                if (response.status == HttpStatus.CREATED.value()) {

                    logger.infoM("Subgroup: ${subGroup.name} created successfully")
                    subGroup.id = CreatedResponseUtil.getCreatedId(response)
                    val resource = realmResource.groups().group(subGroup.id)
                    keycloakGroupService.assignRealmRolesToGroup(subGroup, resource, realmResource)
                    keycloakGroupService.assignClientRolesToGroup(subGroup, resource, realmResource)
                    response.close()
                    createSubGroupWithAssignRoles(
                        subGroup,
                        resource,
                        realmResource
                    )
                } else {
                    response?.close()
                    logger.infoM("Creating Subgroup: ${subGroup.name} not failed")
                }
            }
        } catch (ex: Exception) {
            logger.errorM("Error creating subgroups for group $groupName :: ${ex.message}", ex)
        } finally {
            response?.close()
        }
    }

}

