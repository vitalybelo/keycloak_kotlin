package vitos.local.keycloak_kotlin.interfaces

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersRequestDto
import vitos.local.keycloak_kotlin.models.dormant.DeleteUsersResponseDto


@Tag(
    name = "KeycloakRestController",
    description = "API управления учётными данными пользователей и паролями"
)
@RequestMapping("/users")
interface KeycloakRestController {


    /**
     * Выполняет замену пароля для заданного в запросе пользователя. Метод учитывает наличие
     * настроек политик безопасности для сброса паролей, поэтому вначале сохраняет действующие
     * политики, затем обнуляет политики для области (realm), выполняет сброс пароля на любой
     * заданный и затем восстанавливает дефолтные для области
     *
     * @param userName username пользователя (если не задано, берется из токена)
     * @param password новый пароль пользователя (если не задано, устанавливается как "1")
     * @param headers карта заголовков http запроса
     * @return строку сообщения о выполнении и статус выполнения
     */
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Password changed successfully", content = [Content()]),
            ApiResponse(responseCode = "404", description = "User not found in keycloak", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/change-password")
    @Operation(summary = "Изменение пароля для пользователя в обход установленных политик и ограничений")
    fun changeUserPassword(
        @Parameter(description = "имя пользователя")
        @RequestParam("user", required = false) userName: String? = null,
        @Parameter(description = "новый пароль")
        @RequestParam("password", required = false, defaultValue = "1") password: String? = "1",
        @RequestHeader headers: Map<String, String>
    ): ResponseEntity<Any>


    /**
     * Выполняет создание пользователя в Keycloak. Метод всегда пытается создать нового пользователя стандартным
     * методом keycloak rest api. Если такой пользователь уже существует, мы обрабатываем ошибку и ищем пользака
     * среди существующих. В любом случае мы возвращаем сущность пользователя, нового или существующего
     *
     * @param user сущность пользователя Keycloak для создания
     * @return сущность нового (201) или существующего (200) пользователя Keycloak
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201", description = "Created successfully", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = UserRepresentation::class)))
                    ))]
            ),
            ApiResponse(responseCode = "200", description = "User found as already existing", content = [Content()]),
            ApiResponse(responseCode = "400", description = "Invalid request parameters", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Internal error not defined", content = [Content()])
        ]
    )
    @PostMapping("/create")
    @Operation(summary = "Создание нового пользователя или чтение если он уже существует")
    fun createKeycloakUser(
        @Parameter(description = "сущность для нового пользователя")
        @RequestBody(required = true) user: UserRepresentation?
    ): ResponseEntity<Any>


    /**
     * Метод возвращает userinfo пользователя Keycloak.
     * Идентификатор пользователя передается в метод параметром, в случае если этот параметр null или
     * пустой, метод извлекает идентификатор пользователя ищ токена и возвращает данные для него
     * Статусы выполнения: 200 - успешно, 400 bad request, 404 - user not found, 500 - server error
     *
     * @param userId пользователя
     * @param headers заголовки http запроса
     * @return результат возвращаемый конечной точкой userinfo
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Карта с данными userinfo пользователя", content = [
                    (Content(
                        mediaType = "application/json",
                        array = (ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "400", description = "Некорректные данные запроса", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Пользователь не найден", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @RequestMapping(value = ["/{user_id}/info", "/info"], method = [RequestMethod.GET])
    @Operation(summary = "Возвращает расширенную brute-force информацию о пользователе из Keycloak")
    fun getUserInfo(
        @PathVariable("user_id", required = false) userId: String?,
        @RequestHeader headers: Map<String, String>
    ): ResponseEntity<Any>


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя по этому идентификатору
     *
     * @param authentication класс аутентификации для получения токена
     * @return сущность пользователя keycloak - UserRepresentation
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Данные пользователя получены", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @GetMapping("/representation")
    @Operation(summary = "Возвращает информацию о пользователе из Keycloak")
    fun getUserRepresentation(authentication: Authentication): ResponseEntity<Any>


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя по этому идентификатору, заполняя
     * пропущенные карты ролей и списки групп
     *
     * @param headers карта заголовков http запроса
     * @return сущность пользователя keycloak - UserRepresentation (дополненная)
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Данные пользователя получены", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @GetMapping("/representation/full")
    @Operation(summary = "Возвращает полную информацию о пользователе из Keycloak")
    fun getFullUserRepresentation(@RequestHeader headers: Map<String, String>): ResponseEntity<Any>


    /**
     * Извлекает из токена доступа идентификатор пользователя Keycloak.
     * Затем выполняет чтение учетных данных пользователя с расширенной информацией по brute-force.
     *
     * @return расширенную сущность пользователя keycloak - UserRepresentation
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Данные пользователя получены", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @GetMapping("/brute-force/representation")
    @Operation(summary = "Возвращает расширенную brute-force информацию о пользователе из Keycloak")
    fun getExtendedUserRepresentation(): ResponseEntity<Any>


    /**
     * Выполняет чтение списка учетных данных пользователей с расширенной информацией по brute-force.
     *
     * @return расширенную сущность пользователя keycloak - UserRepresentation
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Список данных пользователя получены", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @GetMapping("/brute-force/list")
    @Operation(summary = "Возвращает список пользователей с расширенной информацией по блокировкам brute-force")
    fun getExtendedUserRepresentationList(): ResponseEntity<Any>


    /**
     * Выполняет поиск пользователя по заданному атрибуту, а затем добавляет или обновляет карту атрибутов
     * значениями, переданным в теле запроса
     *
     * @param key ключ атрибута для поиска пользователя
     * @param value значение атрибута для поиска пользователя
     * @param attributesMap карта атрибутов пользователя для обновления
     * @return возвращает статус выполнения
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Пользователь найден, обновление выполнено успешно", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "400", description = "Неверные параметры запроса", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Пользователь не найден", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Ошибка обновления атрибутов", content = [Content()])
        ]
    )
    @RequestMapping("/attributes", method = [RequestMethod.POST, RequestMethod.PUT])
    @Operation(summary = "Выполняет поиск пользователя по заданному атрибуту и обновляет карту атрибутов")
    fun changeUserAttributes(
        @RequestParam(required = true) key: String?,
        @RequestParam(required = true) value: String?,
        @RequestBody(required = false) attributesMap: Map<String, List<String>>?
    ): ResponseEntity<Any>


    /**
     * Выполняет формирование списка всех ролей групп, которые иерархически закреплены пользователю.
     * Данные пользователя извлекаются из токена доступа, переданного в headers запроса. Если в метод
     * не передана карта заголовков, токен извлекается из контекста безопасности spring security.
     *
     * @param headers заголовки http запроса
     * @return список ролей всех групп, включая дочерние, которые закреплены для пользователя
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Список ролей получен, выполнено успешно", content = [
                    (Content(
                        mediaType = "application/json", array = (
                                ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Не обнаружен токен и данные пользователя ",
                content = [Content()]
            ),
            ApiResponse(responseCode = "404", description = "Пользователь не найден", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Ошибка обновления атрибутов", content = [Content()])
        ]
    )
    @RequestMapping("/groups/role-list", method = [RequestMethod.GET])
    @Operation(summary = "Выполняет формирование списка всех ролей групп, которые иерархически закреплены пользователю.")
    fun findGroupAssignedRoleList(@RequestHeader headers: Map<String, String>? = null): ResponseEntity<Any>


    /**
     * Метод возвращает список сущностей пользователей Keycloak у которых совпадают значения атрибута.
     * Поиск пользователей выполняется по заданному атрибуту. Найденный список возвращается с ответом 200
     *
     * @param key ключ атрибута для поиска пользователя
     * @param value значение атрибута для поиска пользователя
     * @return список сущностей найденных пользователей Keycloak
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Список сущностей пользователей, удовлетворяющих критерию поиска",
                content = [
                    (Content(
                        mediaType = "application/json",
                        array = (ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "400", description = "Некорректные данные запроса", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Ни один пользователь не найден", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @RequestMapping(value = ["/list/by-attributes"], method = [RequestMethod.GET])
    @Operation(summary = "Возвращает список сущностей пользователей Keycloak по заданному атрибуту")
    fun getUserListByAttribute(
        @RequestParam(required = true) key: String?,
        @RequestParam(required = true) value: String?,
    ): ResponseEntity<Any>


    /**
     * Метод выполняет обновление в карте атрибутов на каждого пользователя Keycloak переданного в списке.
     *
     * @param userList список сущностей найденных пользователей Keycloak
     * @return статус выполнения и сообщение
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Выполнено успешно", content = [
                    (Content(
                        mediaType = "application/json",
                        array = (ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "400", description = "Некорректные данные запроса", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @RequestMapping(value = ["/list/by-attributes"], method = [RequestMethod.PUT])
    @Operation(summary = "Выполняет изменение атрибутов для каждого переданного в списке пользователя Keycloak")
    fun updateUserListByAttribute(
        @RequestBody userList: List<Map<String, Any>?>?
    ): ResponseEntity<Any>


    /**
     * Метод выполняет поиск пользователей по заданному списку атрибутов, переданных в метод.
     * Для каждого найденного пользователя, вызывается метода REST API удаления из Keycloak

     * @param abscustIdValues список значений атрибута abscust_id для поиска пользователя
     * @return список значений abscust_id и статус выполнения логики удаление
     */
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200", description = "Выполнено успешно", content = [
                    (Content(
                        mediaType = "application/json",
                        array = (ArraySchema(schema = Schema(implementation = Any::class)))
                    ))]
            ),
            ApiResponse(responseCode = "400", description = "Некорректные данные запроса", content = [Content()]),
            ApiResponse(responseCode = "404", description = "Ни один пользователь не найден", content = [Content()]),
            ApiResponse(responseCode = "500", description = "Непредвиденная ошибка", content = [Content()])
        ]
    )
    @RequestMapping(value = ["/by-attribute-list"], method = [RequestMethod.DELETE])
    @Operation(summary = "Выполняет поиск пользователей по атрибуту и удаляет каждого найденного из Keycloak")
    fun deleteUsersByAttributeList(
        @RequestBody(required = false) abscustIdValues: DeleteUsersRequestDto?
    ): ResponseEntity<out Collection<DeleteUsersResponseDto>>

}