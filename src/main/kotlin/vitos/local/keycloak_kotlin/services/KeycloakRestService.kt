package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.*
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import vitos.local.keycloak_kotlin.authorization.AccessTokenService
import vitos.local.keycloak_kotlin.client.KeycloakBranchClient
import vitos.local.keycloak_kotlin.constants.Constants.Companion.BAD_REQUEST
import vitos.local.keycloak_kotlin.constants.Constants.Companion.FATAL_ERROR
import vitos.local.keycloak_kotlin.constants.Constants.Companion.NOT_FOUND
import vitos.local.keycloak_kotlin.handlers.ParameterChecker
import vitos.local.keycloak_kotlin.logging.Log
import vitos.local.keycloak_kotlin.models.BruteForceUserRepresentation
import vitos.local.keycloak_kotlin.models.CompositeRoles
import vitos.local.keycloak_kotlin.models.KeycloakTokenService
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersEnum
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersEventDto
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersResponseDto
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale.getDefault
import javax.management.timer.Timer


@Service
@Suppress("unused", "DuplicatedCode")
class KeycloakRestService(

    @param:Value($$"${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private val issuerURL: String? = null,
    @param:Value($$"${keycloak.server.url}")
    private val keycloakServerURL: String? = null,
    @param:Value($$"${keycloak.admin.realm}")
    private val keycloakRealm: String? = null,
    @param:Value($$"${dormant.delete.ft-userdel-event:false}")
    private val isDeleteEventToggleON: Boolean,
    @param:Value($$"${dormant.delete.last-enter-time-period:48}")
    private val lastEnterTimePeriodHours: Long,

    private val realmResource: RealmResource,
    private val restTemplate: RestTemplate,
    private val accessTokenService: AccessTokenService,
    private val keycloakTokenService: KeycloakTokenService,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val parameterChecker: ParameterChecker,
    private val keycloakBranchClient: KeycloakBranchClient

) {

    companion object: Log()
    private val lastEnterTimePeriodMillis = lastEnterTimePeriodHours * Timer.ONE_HOUR
    val enterTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ssXXX")


    /**
     * Выполняет замену пароля для заданного в запросе пользователя. Метод учитывает наличие
     * настроек политик безопасности для сброса паролей, поэтому вначале сохраняет действующие
     * политики, затем обнуляет политики для области (realm), выполняет сброс пароля на любой
     * заданный и затем восстанавливает дефолтные для области
     *
     * @param username username пользователя
     * @param password новый пароль пользователя
     * @param headers карта заголовков http запроса
     * @return строку сообщения о выполнении и статус выполнения
     */
    fun changeUserPassword(
        username: String?,
        password: String?,
        headers: Map<String, String>,
    ): ResponseEntity<Any> {

        fun notFoundResponse(message: String): ResponseEntity<Any> {
            return ResponseEntity(message, HttpStatus.NOT_FOUND)
        }

        val userName = username
            ?: accessTokenService.assign(headers)?.login
            ?: return notFoundResponse("Parameter username missed")

        logger.infoM("Changing password procedure for: $userName is starting...")
        try {
            // Ищем пользователя и получаем ресурс администрирования пользователя и области сервисов
            val user = realmResource.users().searchByUsername(userName, true).firstOrNull()
                ?: return notFoundResponse("User not found")

            val usersResource = realmResource.users().get(user.id)
                ?: return notFoundResponse("Impossible to receive user resource")

            val realm: RealmRepresentation = realmResource.toRepresentation()
                ?: return notFoundResponse("Impossible to receive realm representation")

            // сохраняем существующие политики установленные для пароля и сбрасываем их временно
            val passwordPolicies: String? = realm.passwordPolicy
            realm.passwordPolicy = ""
            realmResource.update(realm)
            logger.infoM("Realm password policies reset ...")

            // создаем новую сущность для пароля пользователя типа PASSWORD и выполняем сброс пароля
            val credential = CredentialRepresentation()
            credential.type = CredentialRepresentation.PASSWORD
            credential.value = password
            credential.isTemporary = false

            usersResource.resetPassword(credential)

            // восстанавливаем политики для паролей до дефолтных для области сервисов
            realm.passwordPolicy = passwordPolicies
            realmResource.update(realm)
            logger.infoM("Realm password policies restored ...")

            return ResponseEntity("Password changed successfully for user: $userName", HttpStatus.OK)

        } catch (ex: Exception) {
            logger.errorM("Error during changing password, message = ${ex.message}, cause = ${ex.cause}")
        }
        return ResponseEntity("Error during changing password", HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет создание пользователя в keycloak.
     * Вначале метод пытается создать нового пользователя. Если не заканчивается кодом 201 = создано,
     * выполняется поиск пользователя в keycloak и при успешном выполнении - возвращается сущность
     *
     * @param user сущность пользователя keycloak
     * @return сущность созданного или найденного пользователя, или ошибка
     */
    fun createKeycloakUser(user: UserRepresentation): ResponseEntity<Any> {

        val userName = user.username
        try {
            realmResource.users().create(user).use { response ->
                if (response.status == 201) {
                    val userId = CreatedResponseUtil.getCreatedId(response)
                    if (!userId.isNullOrBlank()) {
                        logger.infoM(">>>> User $userName created id = $userId")
                        realmResource.users()?.get(userId)?.toRepresentation()?.also {
                            return ResponseEntity(it, HttpStatus.CREATED)
                        }
                    }
                }
                logger.infoM(">>>> Create failed, search existing by username :: $userName")
                realmResource.users().searchByUsername(userName, true).firstOrNull()?.also {
                    return ResponseEntity(it, HttpStatus.OK)
                }
            }
        } catch (e: Exception) {
            logger.infoM(">>>> Fatal error creating user :: $userName")
        }
        return ResponseEntity("Fatal error creating user in keycloak", HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя по этому идентификатору
     *
     * @param authentication класс аутентификации для получения токена
     * @return сущность пользователя keycloak - UserRepresentation
     */
    fun getUserRepresentation(authentication: Authentication): ResponseEntity<Any> {

        val accessToken = accessTokenService.assign(authentication)
        if (accessToken != null) {
            getUserRepresentationPrivate(accessToken.userId)?.let {
                return ResponseEntity(it, HttpStatus.OK)
            }
            return ResponseEntity("Пользователь не найден", HttpStatus.NOT_FOUND)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя по этому идентификатору
     * Далее выполняется обогащение сущности ролями и группами пользователя
     *
     * @param headers карта заголовков http запроса
     * @return сущность пользователя keycloak - UserRepresentation (дополненная)
     */
    fun getFullUserRepresentation(headers: Map<String, String>): ResponseEntity<Any> {

        accessTokenService.assign(headers)?.userId?.let { userId ->
            getUserRepresentationPrivate(userId)?.let { user ->

                user.groups = getUserGroupsAssign(userId)

                val userRoles = getUserRolesMapping(userId)
                user.realmRoles = userRoles.first
                user.clientRoles = userRoles.second

                return ResponseEntity(user, HttpStatus.OK)
            }
            return ResponseEntity(NOT_FOUND, HttpStatus.NOT_FOUND)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет запрос в расширенный админ клиент Keycloak для получения списка всех эффективных ролей
     * пользователя (включает все списки ролей композитных ролей и роли назначенные через группы)
     *
     * @return список сущностей композитных ролей
     */
    fun getEffectiveUserRoles(): ResponseEntity<Any> {

        val user = accessTokenService.assign()
            ?: return ResponseEntity(BAD_REQUEST, HttpStatus.BAD_REQUEST)
        val userId = user.userId
            ?: return ResponseEntity(NOT_FOUND, HttpStatus.NOT_FOUND)

        try {
            logger.infoM("Start collect effective roles for user = ${user.displayName}")
            val effectiveRoles = getEffectiveUserRolesList(userId)
            logger.debugM("Effective roles list received for user = ${user.displayName} :: $effectiveRoles")

            if (effectiveRoles != null) {

                getUserResource(userId)?.let { userResource ->
                    logger.infoM("Start add realm roles to final list for user = ${user.displayName}")
                    userResource.roles().realmLevel().listEffective()?.let { realmRoles ->
                        realmRoles.forEach { roleRepresentation ->
                            logger.infoM("Add realm role = ${roleRepresentation.name}")
                            effectiveRoles.add(CompositeRoles().apply {
                                id = roleRepresentation.id
                                role = roleRepresentation.name
                                description = roleRepresentation.description
                            })
                        }
                    }
                    userResource.roles().all.clientMappings?.forEach { (key, value) ->
                        logger.infoM("Start add client roles to final list for user = ${user.displayName}")
                        value.mappings.forEach { roleRepresentation ->
                            logger.infoM("Add client role = ${roleRepresentation.name}")
                            effectiveRoles.add(CompositeRoles().apply {
                                id = roleRepresentation.id
                                role = roleRepresentation.name
                                client = key
                                clientId = value.id
                                description = roleRepresentation.description
                            })
                        }
                    }
                }
                return ResponseEntity(
                    effectiveRoles.sortedBy { it.role?.lowercase(getDefault()) },
                    HttpStatus.OK
                )
            }
        } catch (ex: Exception) {
            logger.errorM("Error during total effective roles list, message = ${ex.message}, cause = ${ex.cause}")
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет чтение учетной записи пользователя из админки Keycloak
     * @param userId идентификатор пользователя
     * @return сущность учётной записи
     */
    private fun getUserRepresentationPrivate(userId: String?): UserRepresentation? {
        if (!userId.isNullOrEmpty()) {
            try {
                return realmResource.users()?.get(userId)?.toRepresentation()
            } catch (ex: Exception) {
                logger.errorM("Error during getting user representation :: message = ${ex.message}, cause = ${ex.cause}")
            }
        }
        return null
    }


    /**
     * Читает список групп, к которым присоединен пользователь
     * @param userId идентификатор пользователя
     * @return список групп пользователей (с полным путем до root)
     */
    private fun getUserGroupsAssign(userId: String): List<String> {

        try {
            return realmResource.users().get(userId).groups().map { group -> group.path }.toList()
        } catch (ex: Exception) {
            logger.errorM(">>>> Error getting user groups assign :: message ${ex.message}, cause = ${ex.cause}")
        }
        return emptyList()
    }


    /**
     * Читает список ролей области, назначенных пользователю заданному по идентификатору userId
     * @param userId идентификатор пользователя (заведомо корректный)
     * @return список realm ролей пользователя
     */
    private fun getUserRealmRolesAsList(userId: String): List<String> {

        try {
            val roleMappingResource = realmResource.users().get(userId).roles()
            if (roleMappingResource != null) {
                return roleMappingResource.realmLevel().listEffective().stream().map { role -> role.name }.toList()
            }
        } catch (ex: Exception) {
            logger.errorM(">>>> Error getting user's realm roles, message = ${ex.message}, cause = ${ex.cause}")
        }
        return emptyList()
    }


    private fun getUserClientsRolesAsList(userId: String): Map<String, MutableList<String>> {

        val clientsRoles: MutableMap<String, MutableList<String>> = HashMap()
        try {
            realmResource.users().get(userId).roles().all.clientMappings?.forEach { (key, value) ->
                clientsRoles[key] = value.mappings.map(RoleRepresentation::getName).toMutableList()
            }
        } catch (ex: Exception) {
            logger.errorM("Error getting user's client roles, message = ${ex.message}, cause = ${ex.cause}")
        }
        return clientsRoles
    }


    /**
     * Выполняет чтение всех ролей области и всех клиентских ролей, которые назначены пользователю
     * @param userId идентификатор пользователя
     * @return пару: список ролей области, карту клиентских ролей
     */
    private fun getUserRolesMapping(userId: String): Pair<List<String>, Map<String, List<String>>> {

        val realmRoles = getUserRealmRolesAsList(userId)
        val clientsRoles = getUserClientsRolesAsList(userId)
        return Pair(realmRoles, clientsRoles)
    }


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя с расширенной информацией по brute-force.
     * @return сущность пользователя с информацией по brute-force BruteForceUserRepresentation
     */
    fun getExtendedUserRepresentation(): ResponseEntity<Any> {

        val userId = accessTokenService.assign()?.userId
        val bruteForceUserList: List<BruteForceUserRepresentation>? = getBruteForceUserList()
        if (userId != null && bruteForceUserList != null) {

            bruteForceUserList.stream()
                .filter { user -> user.id.equals(userId) }.findFirst().orElse(null)?.let {
                    return ResponseEntity(it, HttpStatus.OK)
                }
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет чтение списка учетных данных пользователя с расширенной информацией по brute-force.
     * @return список сущностей пользователя с информацией по brute-force
     */
    fun getExtendedUserRepresentationList(): ResponseEntity<Any> {

        getBruteForceUserList()?.let {
            return ResponseEntity(it, HttpStatus.OK)
        }
        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
    }


    /**
     * Выполняет запрос в keycloak rest api ext-ui для получения списка пользователей с расширенно информацией
     * по временным блокировкам brute-force, и статусом блокировок.
     * @return список сущностей пользователей Keycloak
     */
    private fun getBruteForceUserList(): List<BruteForceUserRepresentation>? {

        try {
            val headers: HttpHeaders = keycloakTokenService.getOauth2Headers()
            val requestEntity = HttpEntity<Void>(headers)
            val responseType =
                object : ParameterizedTypeReference<List<BruteForceUserRepresentation>>() {}
            val uriString =
                StringBuilder(getKeycloakExtURI()).append("/brute-force-user?first=0&max=1000000").toString()

            val res = restTemplate.exchange(
                uriString,
                HttpMethod.GET,
                requestEntity,
                responseType
            )
            if (res.statusCode == HttpStatus.OK && res.body is List<BruteForceUserRepresentation>) return res.body

        } catch (ex: Exception) {
            logger.errorM(">>>> Request to Keycloak UI-EXT failed, message = ${ex.message}, cause = ${ex.cause}")
        }
        return null
    }


    /**
     * Выполняет запрос в расширенный админ клиент Keycloak для получения списка всех эффективных ролей
     * пользователя (включает все списки ролей композитных ролей и роли назначенные через группы)
     *
     * @param userId идентификатор пользователя
     * @return список сущностей композитных ролей
     */
    private fun getEffectiveUserRolesList(userId: String): HashSet<CompositeRoles>? {

        try {
            val headers: HttpHeaders = keycloakTokenService.getOauth2Headers()
            val requestEntity = HttpEntity<Void>(headers)
            val responseType =
                object : ParameterizedTypeReference<List<CompositeRoles>>() {}

            val uriString = StringBuilder(getKeycloakExtURI())
                .append("/effective-roles/users/").append(userId).append("?first=0&max=10000&search=").toString()

            val res = restTemplate.exchange(
                uriString,
                HttpMethod.GET,
                requestEntity,
                responseType
            )
            if (res.statusCode == HttpStatus.OK && res.body is List<CompositeRoles>) {
                return res.body?.toHashSet()
            }
        } catch (ex: Exception) {
            logger.errorM("Error during getting user composite roles, message = ${ex.message}, cause = ${ex.cause}")
        }
        return null
    }


    private fun getKeycloakExtURI(): String = "$keycloakServerURL/admin/realms/$keycloakRealm/ui-ext"


    /**
     * В начале метод выполняет поиск пользователя Keycloak по заданному атрибуту. Если пользователь найден,
     * запрашивается ресурс управления пользователем и обновляются выборочно заданные в запросе атрибуты.
     *
     * @param key ключ атрибута для поиска
     * @param value значение атрибута для поиска
     * @param attributesMap выборочные атрибуты для обновления/добавления
     *
     * @return статус выполнения и полную карту атрибутов пользователя
     */
    fun changeUserAttributes(
        key: String?,
        value: String?,
        attributesMap: Map<String, List<String>>
    ): ResponseEntity<Any> {

        findUserByAttributes(key, value)?.let { userRepresentation ->
            try {
                val userId = userRepresentation.id
                getUserResource(userId)?.let { userResource ->

                    attributesMap.forEach { (key, value) ->
                        userRepresentation.attributes[key] = value
                    }
                    userResource.update(userRepresentation)
                    return ResponseEntity(userRepresentation, HttpStatus.OK)
                }
            } catch (ex: Exception) {
                logger.errorM(">>>> Changing user attributes failed: message = ${ex.message}, cause = ${ex.cause}")
                return ResponseEntity("Error updating user attributes", HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
        val errorMsg = ">>>> User with attribute key = $key, value = $value not found"
        logger.errorM(errorMsg)
        return ResponseEntity(errorMsg, HttpStatus.NOT_FOUND)
    }


    /**
     * Метод выполняет поиск пользователя по заданному ключу и значению атрибута
     *
     * @param key ключ атрибута для поиска
     * @param value значение атрибута для поиска
     * @return сущность пользователя Keycloak, или null если совпадение не найдено
     */
    private fun findUserByAttributes(key: String?, value: String?): UserRepresentation? {

        return realmResource.users().searchByAttributes("$key:\"$value\"", true).firstOrNull()
    }


    /**
     * Метод возвращает список сущностей пользователей Keycloak у которых совпадают значения атрибута.
     * Поиск пользователей выполняется по заданному атрибуту. Найденный список возвращается с ответом 200
     *
     * @param key ключ атрибута для поиска пользователя
     * @param value значение атрибута для поиска пользователя
     * @return список сущностей найденных пользователей Keycloak
     */
    fun findUserListByAttributes(
        key: String?,
        value: String?
    ): ResponseEntity<Any> {

        if (key != null && value != null) {
            try {
                realmResource.users()
                    .searchByAttributes("$key:$value", true)?.let { userList ->
                        if (userList.isNotEmpty()) {
                            return ResponseEntity(userList, HttpStatus.OK)
                        }
                    }
                return ResponseEntity(NOT_FOUND, HttpStatus.NOT_FOUND)
            } catch (ex: Exception) {
                logger.errorM(">>>> Error during searching user list by attributes, message = ${ex.message}, cause = ${ex.cause}")
                logger.debugM(">>>> DEBUG :: ", ex)
            }
            return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
        }
        return ResponseEntity(BAD_REQUEST, HttpStatus.BAD_REQUEST)
    }


    /**
     * Метод выполняет обновление карты атрибутов для каждого пользователя Keycloak переданного в списке.
     *
     * @param userList список сущностей найденных пользователей Keycloak
     * @return статус выполнения и сообщение
     */
    fun updateUserListByAttributes(userList: List<Map<String, Any>>): ResponseEntity<Any> {

        if (userList.isNotEmpty()) {

            val updatedUsers = mutableListOf<String>()
            userList.forEach { user ->

                val userId = user["id"] as String
                try {
                    val userResource = realmResource.users().get(userId)
                    val representation = userResource.toRepresentation()

                    val attributesMap = user["attributes"] as Map<*, *>
                    attributesMap.forEach { (key, values) ->
                        if (key is String && values is List<*>) {
                            val valueList = values.stream().map { v -> v as String }.toList()
                            representation.attributes[key] = valueList
                        }
                    }
                    userResource.update(representation)
                    updatedUsers.add(userId)

                } catch (ex: Exception) {
                    logger.errorM(">>>> Impossible to change attribute for user id = $userId, message = $ex.message, cause = ${ex.cause}")
                }
            }
            return ResponseEntity("Успешно обновленные пользователи = $updatedUsers", HttpStatus.OK)
        }
        return ResponseEntity(BAD_REQUEST, HttpStatus.BAD_REQUEST)
    }


    /**
     * Метод возвращает userinfo пользователя Keycloak.
     * Идентификатор пользователя передается в метод параметром, в случае если этот параметр null или
     * пустой, метод извлекает идентификатор пользователя ищ токена и возвращает данные для него
     *
     * @param userId пользователя
     * @param headers заголовки http запроса
     * @return результат возвращаемый конечной точкой userinfo
     */
    fun getUserInfo(userId: String?, headers: Map<String, String>): ResponseEntity<Any> {

        val sid = accessTokenService.getClaims()["sid"]
        logger.infoM("Received access token session id = $sid")
        collectUserId(userId, headers)?.let { keycloakUserId ->
            getUserRepresentationPrivate(keycloakUserId)?.let {

                try {
                    val userId = it.id
                    val userInfo: MutableMap<String, Any> = emptyMap<String, Any>().toMutableMap()

                    userInfo["id"] = userId ?: ""
                    userInfo["username"] = it.username ?: ""
                    userInfo["firstName"] = it.firstName ?: ""
                    userInfo["lastName"] = it.lastName ?: ""
                    userInfo["email"] = it.email ?: ""
                    userInfo["createdTimestamp"] = it.createdTimestamp ?: ""
                    userInfo["enabled"] = it.isEnabled ?: true
                    userInfo["requiredActions"] = it.requiredActions ?: emptyList<String>()
                    userInfo["realm_roles"] = getUserRealmRolesAsList(userId)
                    userInfo["clients_roles"] = getUserClientsRolesAsList(userId)
                    userInfo["groups"] = getUserGroupsAssign(userId)

                    it.attributes.forEach { (k, v) -> userInfo[k] = v.firstOrNull() ?: "" }

                    return ResponseEntity(userInfo, HttpStatus.OK)
                } catch (ex: Exception) {
                    logger.errorM(">>>> getUserInfo() :: undefined error occurred ${ex.message}")
                }
                return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
            }
            return ResponseEntity("Пользователь не найден", HttpStatus.NOT_FOUND)
        }
        return ResponseEntity("Не найден id пользователя", HttpStatus.BAD_REQUEST)
    }


    /**
     * Выбирает какой из переданных в метод getUserInfo идентификаторов пользователя - использовать.
     * Если передан валидный с точки зрения uuid идентификатор как параметр пути - используем его.
     * Если в пути идентификатор не передан, ищем его по утверждению sub в jwt токене. Если и там
     * он не передан - возвращаем ошибку со статусом 400
     *
     * @param userId идентификатор переданный в контексте (необязательный)
     * @param headers заголовки http запроса
     * @return идентификатор пользователя
     */
    private fun collectUserId(userId: String?, headers: Map<String, String>): String? {

        if (parameterChecker.isValidUUID(userId)) {
            return userId
        }
        accessTokenService.assign(headers)?.let { return it.userId }
        return null
    }


    /**
     * Выполняет формирование списка всех ролей групп, которые иерархически закреплены пользователю,
     * включая дочерние группы. Данные пользователя извлекаются из токена доступа, переданного в headers запроса.
     * Если в метод не передана карта заголовков, токен извлекается из контекста безопасности spring security.
     *
     * @param headers заголовки http запроса
     * @return список ролей всех групп, включая дочерние, которые закреплены для пользователя
     */
    fun findGroupAssignedRoleList(headers: Map<String, String>?): ResponseEntity<Any> {

        val userId = headers?.let { accessTokenService.assign(it)?.userId }
            ?: accessTokenService.assign()?.userId
            ?: return ResponseEntity("JWT not found", HttpStatus.BAD_REQUEST)

        val rolesSet = mutableSetOf<String>()
        realmResource.users().get(userId).groups(0, Integer.MAX_VALUE, false)?.let { groups ->
            groups.forEach { recursiveGroups(rolesSet, it) }
        }
        return ResponseEntity(rolesSet.sorted(), HttpStatus.OK)
    }


    private fun recursiveGroups(
        roleSet: MutableSet<String>,
        parentGroup: GroupRepresentation
    ) {
        addGroupRoles(roleSet, parentGroup)
        realmResource.groups().group(parentGroup.id)
            ?.getSubGroups(0, Integer.MAX_VALUE, false)?.let { subGroups ->
                subGroups.forEach { childGroup ->
                    addGroupRoles(roleSet, childGroup)
                    recursiveGroups(roleSet, childGroup)
                }
            }
    }


    private fun addGroupRoles(
        roles: MutableSet<String>,
        group: GroupRepresentation
    ) {
        group.realmRoles?.let { roles.addAll(it) }
        group.clientRoles?.values?.flatMap { it.toList() }?.let { roles.addAll(it) }
    }


    /**
     * Метод выполняет поиск пользователей по заданному списку атрибутов, переданных в метод.
     * Для каждого найденного пользователя, вызывается метода REST API удаления из Keycloak

     * @param key ключ атрибута для поиска пользователя
     * @param requestSet набор значений атрибута для поиска пользователя и удаления
     * @param isHardDelete тестовая заглушка, для исключения удаления пользователей при тестировании
     * @return коллекция значений атрибута и статуса выполнения удаления
     *
     * Статус выполнения удаления:
     * 2 - пользователя нет в Keycloak / пользователь найден и удален успешно,
     * 3 - не требуется удаление пользователя (он найден, обнаружен вход в установленный период)
     * 999 - пользователь найден, но при выполнении удаления произошла непредвиденная ошибка
     * @author Belotserkovskii Vitaly
     */
    fun deleteUsersByAttributeList(

        key: String,
        requestSet: Set<String>,
        isHardDelete: Boolean
    ): ResponseEntity<out Collection<DeleteUsersResponseDto>> {

        var deletedUsers: List<DeleteUsersResponseDto> = listOf()
        runBlocking {
            deletedUsers = deleteUsersConcurrently(key, requestSet, isHardDelete)
            logger.infoM("Deletion procedure finished. Performed = ${deletedUsers.size} users")
        }
        return ResponseEntity(deletedUsers, HttpStatus.OK)
    }

    /**
     * Основной метод, выполняющий логику удаления пользователя из Keycloak.
     * Вначале, выполняется поиск пользователя по заданному ключу и значению атрибута. В метод передается
     * одно значения ключа и список значений - по каждому значению выполняется поиск пользователя, в случае
     * успешного поиска - проверяется время последнего входа, если оно не превышает лимит, пользователь не
     * удаляется. Иначе, пользователь удаляется из Keycloak и выполняется отправка сообщения SFD об удалении
     * по явной причине = DOR
     *
     * @param key ключ атрибута для поиска пользователя
     * @param requestSet список уникальных значений атрибута для поиска пользователя
     * @param isHardDelete тестовая заглушка, для исключения удаления пользователей при тестировании
     * @return коллекция значений атрибута и статуса выполнения удаления
     * @author Belotserkovskii Vitaly
     *
     * Статус выполнения удаления:
     * 2 - пользователя нет в Keycloak / пользователь найден и удален успешно,
     * 3 - не требуется удаление пользователя (он найден, но обнаружен вход в установленный период)
     * 999 - пользователь найден, но при выполнении удаления произошла непредвиденная ошибка
     */
    suspend fun deleteUsersConcurrently(

        key: String,
        requestSet: Set<String>,
        isHardDelete: Boolean
    ): List<DeleteUsersResponseDto> {

        return supervisorScope {
            val deferredResults = requestSet.map { value ->
                async(Dispatchers.IO) {
                    try {
                        val user = findUserByAttributes(key, value)
                        if (user != null) {
                            // пользователь найден, проверяем логику
                            if (isUserAliveByEnterTime(user)) {
                                // оказывается что пользователь недавно входил в ДБО (задано параметром = 48 часов)
                                return@async DeleteUsersResponseDto(value, DeleteUsersEnum.STILL_ALIVE.status)
                            }
                            // выполняем удаление пользователя из Keycloak
                            if (isHardDelete) {
                                realmResource.users().delete(user.id)
                            }
                            logger.debugM("User :: ${user.username}, found and successfully deleted from Keycloak")
                            // выполняем отправку в очередь сообщение об удалении
                            if (isDeleteEventToggleON) {
                                val dataExportEvent = DeleteUsersEventDto(value)
                                logger.debugM("Event data exported : ${dataExportEvent.toDebugString()}")
                                // TODO ставим сюда отправку сообщения
                            }
                        } else {
                            // пользователь найден, считаем что удаление выполнено
                            logger.debugM("User :: $key = $value not found in Keycloak, consider deleted")
                        }
                        return@async DeleteUsersResponseDto(value, DeleteUsersEnum.DELETED.status)

                    } catch (ex: Exception) {
                        logger.errorM("Delete error occurred for user = $value, message = ${ex.message}, cause = ${ex.cause}")
                    }
                    return@async DeleteUsersResponseDto(value, DeleteUsersEnum.FATAL_ERROR.status)
                }
            }
            // дожидаемся завершения всех async-блоков
            deferredResults.awaitAll()
        }
    }


    /**
     * Выполняет проверку условия, по которому пользователь совершал аутентификацию в течение установленного
     * параметром конфигурации периода времени в часах @see [lastEnterTimePeriodHours]
     * Если в течение установленного периода пользователь совершал вход в систему ДБО, он не будет удален
     * из Keycloak и для него будет установлен соответствующий статус выполнения в ответе
     *
     * @param user сущность пользователя, для которого выполняется проверка
     * @return true если пользователь входил в систему ДБО в установленный период времени [lastEnterTimePeriodHours]
     * @author Belotserkovskii Vitaly
     */
    suspend fun isUserAliveByEnterTime(user: UserRepresentation): Boolean {

        var offlineMillis: Long?
        var offlineHours: Long? = null
        val dateTimeString = user.attributes["enterTime"]?.firstOrNull()
        if (!dateTimeString.isNullOrEmpty()) {
            try {
                val enterTime = OffsetDateTime.parse(dateTimeString, enterTimeFormatter).toInstant()
                offlineMillis = Instant.now().toEpochMilli() - enterTime.toEpochMilli()
                if (logger.isDebugEnabled) {
                    offlineHours = offlineMillis / Timer.ONE_HOUR
                }
                if (offlineMillis < lastEnterTimePeriodMillis) {
                    logger.debugM(
                        "isUserAliveByEnterTime() :: user ${user.username} no longer was offline = $offlineHours hours")
                    return true
                }
            } catch (ex: DateTimeException) {
                logger.errorM("Attribute \"enterTime\" = $dateTimeString for ${user.username} cannot be parsed correctly")
            }
        }
        logger.debugM("isUserAliveByEnterTime() :: user ${user.username} too longer was offline = $offlineHours")
        return false
    }


    private fun getUserResource(userId: String): UserResource? {
        try {
            val resource = realmResource.users().get(userId)
            val representation = resource.toRepresentation()
            logger.infoM("Get user resource :: id = ${representation.id}")
            return resource

        } catch (ex: Exception) {
            logger.errorM(">>>> User with id = $userId not found")
        }
        return null
    }


    /**
     * Метод выполняет поиск пользователей по заданному атрибуту переданному в метод.
     * Для найденного пользователя, вызывается метода REST API Keycloak для изменения заданного атрибута

     * @param searchKey атрибут поиска пользователя
     * @param searchValue значение атрибута поиска пользователя
     * @param modifyKey модифицируемый атрибут пользователя
     * @param modifyValue значение модифицируемого атрибута пользователя
     * @return статус и сообщение
     */
    fun manageUserBranchMigration(
        searchKey: String,
        searchValue: String,
        modifyKey: String,
        modifyValue: String
    ): ResponseEntity<Any> {

        logger.infoM("Request parameters :: searchKey = $searchKey, searchValue = $searchValue ")

        val response = keycloakBranchClient
                .manageMigrationFlag("SpringBootKeycloak", searchKey, searchValue, modifyKey, modifyValue)

        return when (response.statusCode.value()) {
            200 -> ResponseEntity("Успех: Атрибут обновлен", HttpStatus.OK)
            404 -> ResponseEntity("Ошибка: Пользователь не найден", HttpStatus.NOT_FOUND)
            400 -> ResponseEntity("Ошибка: Неверные параметры запроса", HttpStatus.BAD_REQUEST)
            500 -> ResponseEntity("Ошибка: Что-то пошло не так на стороне Keycloak", HttpStatus.INTERNAL_SERVER_ERROR)
            else -> ResponseEntity("Неожиданный статус", response.statusCode)
        }
    }


}

