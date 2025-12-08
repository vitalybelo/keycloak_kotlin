package vitos.local.keycloak_kotlin.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.GroupRepresentation
import org.keycloak.representations.idm.RealmRepresentation
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import vitos.local.keycloak_kotlin.authorization.AccessTokenService
import vitos.local.keycloak_kotlin.constants.Constants.Companion.FATAL_ERROR
import vitos.local.keycloak_kotlin.handlers.ParameterChecker
import vitos.local.keycloak_kotlin.models.BruteForceUserRepresentation
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersEventDto
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersEnum
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersResponseDto
import vitos.local.keycloak_kotlin.services.keycloak.KeycloakTokenService
import java.time.DateTimeException
import java.time.Instant
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
    private val parameterChecker: ParameterChecker

) {

    private val logger = LoggerFactory.getLogger(KeycloakRestService::class.java)
    private val lastEnterTimePeriodMillis = lastEnterTimePeriodHours * Timer.ONE_HOUR


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

        logger.info("Changing password procedure for: $userName is starting...")
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
            logger.info("Realm password policies reset ...")

            // создаем новую сущность для пароля пользователя типа PASSWORD и выполняем сброс пароля
            val credential = CredentialRepresentation()
            credential.type = CredentialRepresentation.PASSWORD
            credential.value = password
            credential.isTemporary = false

            usersResource.resetPassword(credential)

            // восстанавливаем политики для паролей до дефолтных для области сервисов
            realm.passwordPolicy = passwordPolicies
            realmResource.update(realm)
            logger.info("Realm password policies restored ...")

            return ResponseEntity("Password changed successfully for user: $userName", HttpStatus.OK)

        } catch (e: Exception) {
            logger.error("Error during changing password\n {}", e.localizedMessage)
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
                        logger.info(">>>> User {} created :: {}", userName, userId)
                        realmResource.users()?.get(userId)?.toRepresentation()?.also {
                            return ResponseEntity(it, HttpStatus.CREATED)
                        }
                    }
                }
                logger.info(">>>> Create failed, search existing by username :: {}", userName)
                realmResource.users().searchByUsername(userName, true).firstOrNull()?.also {
                    return ResponseEntity(it, HttpStatus.OK)
                }
            }
        } catch (e: Exception) {
            logger.info(">>>> Fatal error creating user :: {}", userName)
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
            return ResponseEntity(null, HttpStatus.NOT_FOUND)
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
            return realmResource.users()?.get(userId)?.toRepresentation()
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
        } catch (e: Exception) {
            logger.error(">>>> Error getting user groups assign: {}", e.message)
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
        } catch (e: Exception) {
            logger.error(">>>> getUserRealmRolesAsList() :: Error getting user realm roles {}", e.message)
        }
        return emptyList()
    }


    private fun getUserClientsRolesAsList(userId: String): Map<String, MutableList<String>> {

        val clientsRoles: MutableMap<String, MutableList<String>> = HashMap()
        realmResource.users().get(userId).roles().all.clientMappings?.forEach { (key, value) ->
            clientsRoles[key] = value.mappings.map(RoleRepresentation::getName).toMutableList()
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
     * Выполняет запрос в keycloak rest api ext-ui списка пользователей я расширенно информацией
     * по временным блокировкам brute-force, с статусом блокировок
     *
     * @param offset смещение пагинации (дефолтное значение = 0)
     * @param limit ограничение пагинации (дефолтное значение = 1000000)
     * @return список сущностей пользователей Keycloak
     */
    private fun getBruteForceUserList(
        offset: Int = 0,
        limit: Int = 1_000_000
    ): List<BruteForceUserRepresentation>? {

        try {
            val headers: HttpHeaders = keycloakTokenService.getOauth2Headers()
            val requestEntity = HttpEntity<Void>(headers)

            val res = restTemplate.exchange(
                "$keycloakServerURL/admin/realms/$keycloakRealm/ui-ext/brute-force-user?first=$offset&max=$limit",
                HttpMethod.GET,
                requestEntity,
                Any::class.java,
                object : ParameterizedTypeReference<Any?>() {
                })

            if (res.statusCode == HttpStatus.OK) {

                val value = objectMapper.writeValueAsString(res.body)
                val bruteForceUserList: List<BruteForceUserRepresentation> = objectMapper.readValue(value)
                return bruteForceUserList
            }
        } catch (e: java.lang.Exception) {
            logger.error(">>>> Request to Keycloak UI-EXT failed >>>> {}", e.message)
        }
        return null
    }


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
        attributesMap: Map<String, List<String>>?
    ): ResponseEntity<Any> {

        findUserByAttributes(key, value)?.let { userRepresentation ->
            try {
                val usersResource = realmResource.users().get(userRepresentation.id)
                attributesMap?.forEach { (key, value) ->
                    userRepresentation.attributes[key] = value
                }
                usersResource.update(userRepresentation)
                return ResponseEntity(userRepresentation.attributes, HttpStatus.OK)

            } catch (ignored: Exception) {
            }
            logger.error(">>>> Error during changing user attributes :: {}", userRepresentation)
            return ResponseEntity("Error updating user attributes", HttpStatus.INTERNAL_SERVER_ERROR)
        }
        logger.error(">>>> User with $key:$value not found")
        return ResponseEntity("User not found", HttpStatus.NOT_FOUND)
    }


    /**
     * Метод выполняет поиск пользователя по заданному ключу и значению атрибута
     *
     * @param key ключ атрибута для поиска
     * @param value значение атрибута для поиска
     * @return сущность пользователя Keycloak, или null если совпадение не найдено
     */
    private fun findUserByAttributes(key: String?, value: String?): UserRepresentation? {

        realmResource.users()
            .searchByAttributes("$key:$value", true)?.let { userList ->
                userList.stream()
                    .filter { user -> user.attributes[key]?.any { s -> s.equals(value) } == true }
                    .findFirst()
                    .orElse(null)
                    ?.let { return it }
            }
        return null
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
                return ResponseEntity(null, HttpStatus.NOT_FOUND)
            } catch (ex: Exception) {
                logger.error(">>>> Error during searching user list by attributes {}", ex.message)
                logger.debug(">>>> DEBUG :: ", ex)
            }
            return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
        }
        return ResponseEntity(null, HttpStatus.BAD_REQUEST)
    }


    /**
     * Метод выполняет обновление в карте атрибутов на каждого пользователя Keycloak переданного в списке.
     *
     * @param userList список сущностей найденных пользователей Keycloak
     * @return статус выполнения и сообщение
     */
    fun updateUserListByAttributes(userList: List<Map<String, Any>?>?): ResponseEntity<Any> {

        if (!userList.isNullOrEmpty()) {
            userList.forEach { user ->
                if (user is Map<String, Any>) {
                    try {
                        val userId = user["id"] as String
                        realmResource.users().get(userId)?.let { userResource ->
                            userResource.toRepresentation()?.let { userRepresentation ->

                                val attributesMap: Any? = user["attributes"]
                                if (attributesMap is Map<*, *>) {
                                    attributesMap.forEach { (key, values) ->
                                        if (key is String && values is List<*>) {
                                            val valueList = values.stream().map { v -> v as String }.toList()
                                            userRepresentation.attributes[key] = valueList
                                        }
                                    }
                                }
                                userResource.update(userRepresentation)
                            }
                        }
                    } catch (ex: Exception) {
                        logger.error(">>>> Error during update user list by attributes {}", ex.message)
                        return ResponseEntity(FATAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
                    }
                } else {
                    logger.warn("Impossible to perform update for user = null")
                }
            }
            return ResponseEntity("Успешно обновлено пользователей = ${userList.size}", HttpStatus.OK)
        }
        return ResponseEntity(null, HttpStatus.BAD_REQUEST)
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
                    logger.error(">>>> getUserInfo() :: undefined error occurred ${ex.message}")
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
            logger.info("Deletion procedure finished. Performed = ${deletedUsers.size} users")
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
                                // оказывается что пользователь недавно входил в ДБО (задано парамером = 48 часов)
                                return@async DeleteUsersResponseDto(value, DeleteUsersEnum.STILL_ALIVE.status)
                            }
                            // выполняем удаление пользователя из Keycloak
                            if (isHardDelete) {
                                realmResource.users().delete(user.id)
                            }
                            logger.debug("User :: ${user.username}, found and successfully deleted from Keycloak")
                            // выполняем отправку в очередь сообщение об удалении
                            if (isDeleteEventToggleON) {
                                val dataExportEvent = DeleteUsersEventDto(value)
                                logger.debug("Event data exported : ${dataExportEvent.toDebugString()}")
                                // TODO ставим сюда отправку сообщения
                            }
                        } else {
                            // пользователь найден, считаем что удаление выполнено
                            logger.debug("User :: $key = $value not found in Keycloak, consider deleted")
                        }
                        return@async DeleteUsersResponseDto(value, DeleteUsersEnum.DELETED.status)

                    } catch (ex: Exception) {
                        logger.error("Delete error occurred for user = $value, message = ${ex.message}, cause = ${ex.cause}")
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
                val instant = Instant.parse(dateTimeString)
                offlineMillis = Instant.now().toEpochMilli() - instant.toEpochMilli()
                if (logger.isDebugEnabled) {
                    offlineHours = offlineMillis / Timer.ONE_HOUR
                }
                if (offlineMillis < lastEnterTimePeriodMillis) {
                    logger.debug(
                        "isUserAliveByEnterTime() :: user ${user.username} no longer was offline = $offlineHours hours")
                    return true
                }
            } catch (ex: DateTimeException) {
                logger.error("Attribute \"enterTime\" = $dateTimeString for ${user.username} cannot be parsed correctly")
            }
        }
        logger.debug("isUserAliveByEnterTime() :: user ${user.username} too longer was offline = $offlineHours")
        return false
    }


}

