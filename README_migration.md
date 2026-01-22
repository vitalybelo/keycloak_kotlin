Методы миграции реализованные для данных Keycloak

### Оглавление:
1. [Экспорт настроек realm](#экспорт-настроек-рабочей-области-сервисов-realm)
2. [Импорт настроек realm](#импорт-настроек-рабочей-области-сервисов-realm)
3. [Удаление realm](#удаление-рабочей-области-сервисов-realm)
4. [Экспорт client scopes из realm](#экспорт-client-scopes-из-рабочей-области)
5. [Импорт client scopes в другой realm](#импорт-client-scopes-в-другую-рабочую-область)
6. [Экспорт realm roles из рабочей области](#экспорт-realm-roles-из-рабочей-области)
7. [Импорт Realm Roles в другую рабочую область](#импорт-realm-roles-в-другую-рабочую-область)
8. [Экспорт Realm Groups из рабочей области](#экспорт-realm-groups-из-рабочей-области)
9. [Импорт Realm Groups в другую рабочую область](#импорт-realm-groups-в-другую-рабочую-область)
10. [Экспорт Clients из рабочей области](#экспорт-clients-из-рабочей-области)
11. [Импорт Clients в другую рабочую область](#импорт-clients-в-другую-рабочую-область)
12. [Экспорт Authentication Flows из рабочей области](#экспорт-authentication-flows-из-рабочей-области)
13. [Импорт Authentication Flows в другую рабочую область](#импорт-authentication-flows-в-другую-рабочую-область)
<br/><br/>

### Экспорт настроек рабочей области сервисов realm
 
<span style="color:cornflowerblue">
GET / keycloak / migrate / realm / configuration
</span>

##### <span style="color:green">Параметры запроса для получения настроек realm</span>

##### <span style="color:goldenrod">Query Params</span>
    realm = название области, для которой делается экспорт
    isMigrateRealmRoles = true или false - включение ролей области в экспорт
    isMigrateClientScopes = true или false - включение client scopes в экспорт
    isMigrateRealmGroups = true или false - включение групп в экспорт
    isMigrateFlows = true или false - включение потоков аутентификации в экспорт

##### <span style="color:goldenrod">Response</span>
    200 OK: Возвращает объект RealmRepresentation (JSON конфигурации).
    400 BAD REQUEST: Если имя Realm пустое.
    404 NOT FOUND: Если Realm не существует.

##### <span style="color:goldenrod">Описание работы метода</span>
    Этот сервис является «оркестратором»,
    который управляет не только базовыми настройками Realm,
    но и делегирует миграцию связанных сущностей (Роли, Группы, Scopes).

    Алгоритм работы

    Получение экспорта от Keycloak:
    Использует нативный метод realmResource.partialExport(true, false), который выгружает «сырую» конфигурацию.
    Очистка данных (cleanConditionalRealConfiguration):
        На основе флагов exportConditions из полученного объекта удаляются лишние данные.
        Например, если isMigrateRealmRoles = false, поле roles в JSON принудительно зануляется (null). Это делается для того, чтобы мигрировать роли отдельным этапом, а не огромным куском внутри Realm-конфига.

[назад к оглавлению](#оглавление)<br/><br/>

### Импорт настроек рабочей области сервисов realm

<span style="color:cornflowerblue">
POST / keycloak / migrate / realm / configuration
</span>

##### <span style="color:green">Параметры запроса для сохранения настроек realm</span>

##### <span style="color:goldenrod">Query Params</span>
    realm = название создаваемой или обновляемой области
    isMigrateRealmRoles = true или false - включение ролей области в импорт
    isMigrateClientScopes = true или false - включение client scopes в импорт
    isMigrateRealmGroups = true или false - включение групп в импорт
    isMigrateFlows = true или false - включение потоков аутентификации в импорт

##### <span style="color:goldenrod">Body</span>
    realmRepresentation: Импортируемая конфигурация, полученная методом экспорта 

##### <span style="color:goldenrod">Response</span>
    Json настроек созданной или обновленной области

##### <span style="color:goldenrod">Описание работы метода</span>
    Алгоритм работы

    Проверка существования: Сервис проверяет, существует ли уже Realm с таким именем.
    Создание/Обновление ядра (createOrUpdateRealm):

        Выполняется перенос базовых настроек и компонентов.
        Важно: На этом этапе из realmRepresentation удаляются списки ролей, групп и scopes. Они будут загружены позже.

    Частичная миграция (partialRealmMigration): Запускается процесс наполнения Realm связанными данными.
    Формирование отчета: Возвращается RealmImportResponseDto со статистикой (сколько ролей/групп добавлено).

    Детальная логика подпроцессов импорта
    А. Работа с компонентами (updateComponents)

    Keycloak Components (User Federation, LDAP и др.) требуют особого обращения с ID.
    При создании Realm: У всех импортируемых компонентов принудительно обнуляются ID (id = null), чтобы Keycloak сгенерировал новые.
    При обновлении Realm:
        Сервис ищет существующие компоненты по providerId (типу провайдера).
        Если компонент найден, импортируемому объекту присваивается ID существующего (для корректного UPDATE).
        Если не найден — ID обнуляется (для CREATE).

    Б. Очистка привязок потоков (cleanAssignedFlowNames)
    Перед сохранением Realm сервис обнуляет ссылки на дефолтные потоки (browserFlow, registrationFlow и т.д.).
    Причина: Если импортировать Realm, который ссылается на кастомный поток, которого еще нет в системе, возникнет ошибка. Привязка восстанавливается позже или используются дефолтные значения.

    В. Частичная миграция и Ожидание готовности (partialRealmMigration)
    Это критически важный этап. После отправки команды create Realm появляется в API не мгновенно.

        Ожидание (isCreatedRealmResourceReady):
            Используется корутина (runBlocking) с механизмом поллинга.
            Сервис в цикле пытается получить настройки Realm каждые 5 секунд (константа READY_WAIT_LOOP_DELAY).
            Тайм-аут ожидания: 20 секунд (READY_WAIT_COROUTINES_TIMEOUT).

        Последовательная загрузка: Как только Realm становится доступен, последовательно вызываются сервисы для импорта:
            updateRealmRoles (Роли Realm).
            updateClientScopes (Client Scopes).
            updateGroups (Группы).
            assignDefaultGroups (Назначение дефолтных групп, так как их нельзя назначить, пока группы физически не созданы).
            updateAuthenticationFlows (Потоки аутентификации).

    Зависимости и Связи
    Класс активно использует другие сервисы для делегирования задач:

        MigrateRealmRolesService: Миграция ролей.
        MigrateGroupsService / KeycloakGroupService: Миграция и поиск групп.
        MigrateClientScopeService: Миграция Scopes.
        MigrateAuthFlowsService: Миграция сложных потоков авторизации.

[назад к оглавлению](#оглавление)<br/><br/>


### Удаление рабочей области сервисов realm

<span style="color:cornflowerblue">
DELETE / keycloak / migrate / realm / configuration
</span>

##### <span style="color:green">Параметры запроса</span>

##### <span style="color:goldenrod">Query Params</span>
    realm = название области, которую надо удалить

##### <span style="color:goldenrod">Response</span>
    200 OK — удалено успешно.
    404 NOT FOUND — Realm не найден.
    500 ERROR — если удаление заблокировано (например, базой данных).

[назад к оглавлению](#оглавление)<br/><br/>

### Экспорт Client Scopes из рабочей области

<span style="color:cornflowerblue">
GET / keycloak / migrate / realm / client-scopes
</span>

##### <span style="color:green">Параметры запроса</span>

##### <span style="color:goldenrod">Query Params</span>
    realm (String): Название области, из которой производится экспорт.

##### <span style="color:goldenrod">Response</span>
    200 OK: Возвращает объект ClientScopeExportDto, содержащий:
        clientScopes: Полный список объектов ClientScopeRepresentation.
        default: Список имен scopes, которые назначены как Default (применяются ко всем клиентам по умолчанию).
        optional: Список имен scopes, которые назначены как Optional (доступны для запроса клиентами).
    
    400 BAD REQUEST: Если имя Realm пустое.
    404 NOT FOUND: Если Realm не найден или список scopes пуст.

##### <span style="color:goldenrod">Описание работы метода</span>
    Экспорт собирает полную информацию обо всех доступных Client Scopes в Realm, включая их мапперы, и классифицирует их по типу назначения.    

    Алгоритм работы

    1. Сбор данных: Сервис запрашивает у Keycloak полный список scopes (findAll).
    2. Классификация:
        Отдельно запрашиваются списки defaultDefaultClientScopes и defaultOptionalClientScopes.
        Имена из этих списков сохраняются в отдельные поля DTO для восстановления привязок при импорте.

[назад к оглавлению](#оглавление)<br/><br/>

### Импорт Client Scopes в другую рабочую область

<span style="color:cornflowerblue">
POST / keycloak / migrate / realm / client-scopes
</span>

##### <span style="color:green">Параметры запроса</span>
##### <span style="color:goldenrod">Query Params</span>
    realm (String): Целевой Realm.

##### <span style="color:goldenrod">Body</span>
    importedClientScopes (ClientScopeExportDto): Объект с данными экспорта.

##### <span style="color:goldenrod">Response</span>
    Отчет о выполнении? в котором указано:
    1. сколько скоупов создано,
    2. сколько скоупов обновлено,
    3. сколько не удалось импортировать. 

##### <span style="color:goldenrod">Описание работы метода</span>
    Алгоритм работы

    Анализ целевой среды: Сервис загружает список уже существующих в Realm scopes и создает карту Map<Name, Representation> для быстрого поиска.
    Итерация: Проходит по каждому элементу из списка импорта.

    Выбор действия:
        Существует: Если имя scope найдено в карте — вызывается updateClientScopeWithMappers (Обновление).
        Не существует: Если имя не найдено — вызывается createClientScopeWithMappers (Создание).

    Отчетность: Формирует MigrationResponseDto со списками созданных и обновленных сущностей.

    Детальная логика подпроцессов импорта
    А. Создание (createClientScopeWithMappers)
    Используется для создания абсолютно новой сущности.

        Очистка ID: Принудительно устанавливает id = null для самого Client Scope и для всех вложенных protocolMappers. Это критично, так как ID из старого Realm невалидны в новом.
        Создание: Выполняет запрос create.
        Назначение типа: После успешного создания вызывает assignClientScope, чтобы установить тип (Default/Optional).

    Б. Обновление (updateClientScopeWithMappers)
    Используется для актуализации существующего scope. Главная сложность здесь — корректная обработка Protocol Mappers.
    Сохранение ID Scope: Импортируемому объекту присваивается ID существующего в базе объекта (чтобы Keycloak понял, что это update).

    Синхронизация Мапперов:
        Загружаются текущие мапперы существующего scope.
        Алгоритм проходит по импортируемым мапперам:
            Если маппер с таким именем уже есть -> берется его старый ID (для обновления).
            Если маппера нет -> ID ставится в null (для создания нового).

    Апдейт: Вызывается метод .update().
    Назначение типа: Вызывается assignClientScope.

    В. Назначение Default/Optional (assignClientScope)
    Этот метод определяет, должен ли scope применяться автоматически ко всем клиентам.

    Входные данные: Использует списки default и optional из DTO экспорта.
    Логика переключения:

        Назначение DEFAULT:
            Если scope должен быть Default, но сейчас Optional -> сначала удаляется из Optional, затем добавляется в Default.
            Если он уже Default -> действий не требуется.

        Назначение OPTIONAL:
            Если scope должен быть Optional, но сейчас Default -> сначала удаляется из Default, затем добавляется в Optional.
            Если он уже Optional -> действий не требуется.

    Цель: Избежать коллизий, когда scope пытается быть одновременно и Default, и Optional, что недопустимо в Keycloak.

    Особенности обработки данных
        Protocol Mappers: Мигрируются как неотъемлемая часть Client Scope. Логика обновления гарантирует, что мапперы не дублируются, а их настройки актуализируются.
        Независимость: Сервис может быть вызван отдельно или как часть полной миграции Realm (через MigrateRealmService).

[назад к оглавлению](#оглавление)<br/><br/>

### Экспорт Realm Roles из рабочей области

<span style="color:cornflowerblue">
GET / keycloak / migrate / realm / realm-roles
</span>

##### <span style="color:green">Параметры запроса</span>
##### <span style="color:goldenrod">Query Params</span>
    realm (String): Название области, из которой производится выгрузка ролей.

##### <span style="color:goldenrod">Response</span>
    200 OK: Возвращает список объектов RoleRepresentation (найденные роли).
    400 BAD REQUEST: Если имя Realm не задано.
    404 NOT FOUND: Если Realm не найден или список ролей пуст.

##### <span style="color:goldenrod">Описание работы метода</span>
    Экспорт извлекает список ролей из указанного Realm, применяет фильтрацию для исключения системных ролей и сохраняет результат в базу данных миграции.
    Алгоритм работы:

    Получение списка: Запрашивает у Keycloak список всех ролей (list(0, Integer.MAX_VALUE, false)).
    Фильтрация: Применяет логику фильтрации для исключения внутренних ролей Keycloak.

        Условие: В экспорт попадают роли, у которых описание (description) отсутствует (пустое) ИЛИ описание НЕ начинается с символа $.
        Это позволяет отсеять системные роли (обычно начинаются с ${...} или имеют специфические маркеры).

[назад к оглавлению](#оглавление)<br/><br/>


### Импорт Realm Roles в другую рабочую область

<span style="color:cornflowerblue">
POST / keycloak / migrate / realm / realm-roles
</span>

##### <span style="color:green">Параметры запроса</span>

##### <span style="color:goldenrod">Query Params</span>
    realm (String): Целевой Realm.

##### <span style="color:goldenrod">Body</span>
    realmRoles (List<RoleRepresentation>): Список ролей для импорта.

##### <span style="color:goldenrod">Response</span>
    200 OK: Возвращает объект MigrationResponseDto со статистикой:
        Количество созданных ролей.
        Количество обновленных ролей.
        Общее количество обработанных записей.

    400 BAD REQUEST: Если не передано имя Realm или список ролей пуст.
    404 NOT FOUND: Если Realm не найден.

##### <span style="color:goldenrod">Описание работы метода</span>
    Алгоритм работы

        Анализ целевой среды: Сервис загружает список всех существующих в Realm ролей и строит карту Map<Name, RoleRepresentation> для мгновенного поиска.
        Итерация: Проходит по каждой роли из входящего списка.
        Выбор стратегии:
    
            Существует (UPDATE):
                Если роль с таким именем найдена в карте, вызывается метод keycloakRolesService.updateRealmRoles.
                В этот метод передается новая сущность, существующая сущность (для ID) и ресурс управления.
                Счетчик обновленных ролей увеличивается.
    
            Не существует (CREATE):
                Если роль не найдена, вызывается метод keycloakRolesService.createRealmRole.
                Создается новая роль в Realm.
                Счетчик созданных ролей увеличивается.
    
        Отчет: Формируется и возвращается DTO с результатами миграции.

    Зависимости

        KeycloakRolesService: Вспомогательный сервис, который инкапсулирует низкоуровневые вызовы API Keycloak для создания (.create) и обновления (.update) ролей.
        MigrateRealmRolesService управляет логикой "что делать", а KeycloakRolesService — "как делать".

[назад к оглавлению](#оглавление)<br/><br/>

### Экспорт Realm Groups из рабочей области

<span style="color:cornflowerblue">
GET / keycloak / migrate / realm / groups
</span>

##### <span style="color:green">Параметры запроса</span>
##### <span style="color:goldenrod">Query Params</span>
    realm (String): Название области, из которой производится выгрузка.

##### <span style="color:goldenrod">Response</span>
    200 OK: Возвращает список объектов GroupRepresentation (корневые группы со вложенными подгруппами).
    400 BAD REQUEST: Если имя Realm не задано.
    404 NOT FOUND: Если Realm не найден или в нем нет групп.

##### <span style="color:goldenrod">Описание работы метода</span>
    Алгоритм работы

        Сбор данных: 
        Делегирует задачу сервису KeycloakGroupService.collectGroups. Этот метод (предположительно) рекурсивно обходит дерево групп, загружая для каждой группы её роли и атрибуты.
        
        Результат:
        Возвращает полную структуру групп клиенту.

[назад к оглавлению](#оглавление)<br/><br/>

### Импорт Realm Groups в другую рабочую область

<span style="color:cornflowerblue">
POST / keycloak / migrate / realm / groups
</span>

##### <span style="color:green">Параметры запроса</span>
##### <span style="color:goldenrod">Query Params</span>
    realm (String): Целевой Realm.

##### <span style="color:goldenrod">Body</span>
    importGroupList (List<GroupRepresentation>): Список групп для импорта (корневые элементы).

##### <span style="color:goldenrod">Response</span>
    Отчет о выполнении, в котором указано
    1. сколько корневых групп создано (подгруппы создаются как дочерние, но не фигурируют в отчете)
    2. сколько корневых групп обновлено
    3. сколько не удалось импортировать. 

##### <span style="color:goldenrod">Описание работы метода</span>
    Алгоритм работы

    Анализ: Загружает список существующих корневых групп в Realm для сравнения.
    Итерация:
        Обновление: Если корневая группа с таким именем уже есть, вызывается updateGroupWithAssignRoles.
        Создание: Если группы нет, вызывается createGroupWithAssignRoles.

    Отчет: Возвращает DTO со статистикой (создано/обновлено/ошибки).

    Детальная логика подпроцессов импорта
    А. Создание иерархии (createGroupWithAssignRoles и createSubGroupWithAssignRoles)

        Эти методы работают в паре для рекурсивного создания дерева.
        Создание узла:
            У группы (или подгруппы) обнуляется ID.
            Вызывается API создания группы (realmResource.groups().add(...) или parentResource.subGroup(...)).
            Получается ID созданной группы.

        Назначение ролей:
        Сразу после создания для группы вызываются методы назначения ролей:
            assignRealmRolesToGroup: Добавляет глобальные роли.
            assignClientRolesToGroup: Добавляет специфичные для клиентов роли.

        Рекурсия (Подгруппы):
            Метод проверяет наличие subGroups в импортируемом объекте.
            Для каждой подгруппы вызывается метод создания подгруппы (createSubGroupWithAssignRoles), который повторяет этот цикл (Создание -> Роли -> Рекурсия).

    Б. Обновление иерархии (updateGroupWithAssignRoles)
        Сложнейший метод, который синхронизирует существующее дерево с импортируемым.
    
        Обновление атрибутов: Обновляет базовые поля (атрибуты, права доступа) через .update().
        Перезапись ролей:
            Важно: Сначала удаляет ВСЕ существующие роли у группы (через removeGroupRoles), чтобы избежать накопления "мусора" (старых ролей, которых нет в импорте).
            Затем назначает роли заново из импортируемого объекта.
    
        Синхронизация подгрупп:
            Загружает текущие подгруппы.
            Сравнивает их с импортируемыми:
    
                Если подгруппа есть и там, и там -> Рекурсивный вызов updateGroupWithAssignRoles.
                Если подгруппы нет в Realm -> Создание через createSubGroupWithAssignRoles.
                Очистка: Если в Realm есть подгруппы, которых нет в импорте, они удаляются (.remove()).

    В. Удаление ролей (removeGroupRoles)
    Вспомогательный метод для полной очистки ролей перед обновлением.

        Удаляет все Realm-Level роли.
        Удаляет все Client-Level роли, проходя по каждому клиенту.

    Г. Фильтрация подгрупп (getSubGroupList)
    Метод предотвращает дублирование при создании.

        Перед созданием подгрупп проверяет, какие из них уже существуют в родительской группе.
        Возвращает только те, которых реально не хватает (filter !contains), чтобы избежать ошибок "Group already exists".

    Особенности

        Полная синхронизация: Логика обновления (update) гарантирует, что итоговое состояние группы в Keycloak будет в точности соответствовать файлу импорта (лишние роли и подгруппы удаляются).
        Глубина вложенности: Сервис поддерживает любую глубину вложенности групп благодаря рекурсии.
        Независимость ID: При импорте все ID групп и ролей игнорируются или пересчитываются, что позволяет переносить группы между совершенно разными инстансами Keycloak.

[назад к оглавлению](#оглавление)<br/><br/>

### Экспорт Clients из рабочей области

<span style="color:cornflowerblue">
GET / keycloak / migrate / realm / clients
</span>

##### <span style="color:green">Параметры запроса</span>

##### <span style="color:goldenrod">Query Params</span>

    realm (String): Название области (Realm), откуда производится экспорт.
    clientIds (String): Строка, содержащая список client_id (идентификаторов клиентов), которые необходимо экспортировать, разделенные через запятую.

##### <span style="color:goldenrod">Response</span>

    200 OK: Возвращает объект ClientListExportDto со списком найденных клиентов
    400 BAD REQUEST: Если параметры пусты или клиенты не найдены
    404 NOT FOUND: Если указанный Realm не существует

    экспортное DTO со списком подробных json сущностей clients. 
    Для каждого client запрашивается стандартный ClientRepresentation, 
    обогащенный информацией о сервисном пользователе (если он имеется), 
    ролях и настройки авторизации (если они существуют).   

[назад к оглавлению](#оглавление)<br/><br/>

### Импорт Clients в другую рабочую область

<span style="color:cornflowerblue">
POST / keycloak / migrate / realm / clients
</span>

##### <span style="color:green">Параметры запроса</span>

##### <span style="color:goldenrod">Query Params</span>

    realm (String): Целевой Realm.
    stamp (String?): Штамп модификации имени (используется для переименования при дубликатах).
    isAlwaysCreate (Boolean): Флаг принудительного создания нового клиента. Если true, к clientId добавляется суффикс (штамп + "-migrated"), чтобы избежать конфликта имен.

##### <span style="color:goldenrod">Body</span>

    importClients (ClientListExportDto): Объект, содержащий список клиентов для импорта, результат работы метода GET

##### <span style="color:goldenrod">Response</span>

    200 OK: Возвращает ClientImportResponseDto со списками успешно импортированных и ошибочных клиентов.
    400 BAD REQUEST: Некорректные входные данные.
    404 NOT FOUND: Realm недоступен.

##### <span style="color:goldenrod">Описание работы метода</span>

    Основной метод: createOrUpdateRealmClient
    Этот метод является точкой входа для импорта. Он принимает целевой realm, флаг принудительного создания isAlwaysCreate, штамп времени stamp и список клиентов для импорта (importClients).
    Алгоритм работы:

    Валидация: Проверяется, что имя realm и список импортируемых клиентов не пустые. Если они пусты, возвращается ошибка BAD_REQUEST.
    Получение Realm: Запрашивается ресурс Realm. Если он недоступен, возвращается NOT_FOUND.
    Итерация по клиентам: Происходит перебор всех клиентов из списка импорта (importedClientExportDtoList).
    Поиск дубликатов: Для каждого клиента выполняется поиск в текущем Realm по clientId (realmResource.clients().findByClientId(clientId)), чтобы понять, существует ли он уже.

    Обработка флага isAlwaysCreate:
        Если этот флаг установлен в true, процедура игнорирует существование клиента и создает его копию.
        К clientId добавляется суффикс, содержащий штамп и слово "migrated" (например, my-service-20231027-migrated).
        К описанию (description) добавляется пометка (migrated).

    Выбор стратегии (Создание или Обновление):
        Если клиент не найден ИЛИ включен флаг isAlwaysCreate — вызывается метод создания createClientImported.
        Если клиент найден И ПРИ ЭТОМ флаг isAlwaysCreate выключен (false) — вызывается метод обновления updateClientImported.

    Формирование отчета: Результаты (успешно импортированные или ошибки) собираются в объект ClientImportResponseDto, который возвращается клиенту.</span>

    Процедура создания: createClientImported
    Этот метод используется для создания абсолютно нового клиента.

    Подготовка объекта: У импортируемого объекта ClientRepresentation обнуляется id.
    Очистка зависимостей: Перед созданием временно удаляются protocolMappers, authorizationSettings и authenticationFlowBindingOverrides, чтобы избежать конфликтов при первичном создании сущности.
    Создание в Keycloak: Выполняется запрос realmResource.clients().create(...).
    Проверка успеха: Если статус ответа 201 Created, извлекается id созданного клиента.
    Наполнение данными: После успешного создания базовой сущности, последовательно вызываются методы для наполнения клиента деталями:

        createOrUpdateClientProtocolMappers — добавление мапперов.
        clientsService.createOrUpdateClientRoles — создание ролей клиента.
        userService.updateServiceAccountUser — настройка системного пользователя (Service Account).
        updateAuthorizationSettings — настройка прав доступа (Authorization).

    Процедура обновления: updateClientImported
    Этот метод используется, если клиент уже существует и его нужно актуализировать.

    Получение ресурса: Получает ссылку на существующего клиента по его id.
    Обновление конфигурации:

        В импортируемый объект проставляется id существующего клиента.
        Выполняется clientResource.update(...) для обновления базовых полей. Важно: при этом вызове мапперы временно берутся от существующего клиента, чтобы не сломать их до этапа синхронизации.

    Синхронизация компонентов: Аналогично созданию, вызываются методы для обновления мапперов, ролей, сервисного аккаунта и настроек авторизации.

    Детальная логика подпроцессов

    1. Синхронизация Protocol Mappers (createOrUpdateClientProtocolMappers)
    Метод обеспечивает точное соответствие мапперов:
    
        Сравнивает мапперы по имени (name).
        Update: Если маппер с таким именем уже есть — он обновляется данными из импорта.
        Create: Если маппера нет — создается новый.
        Delete: Если у существующего клиента есть мапперы, которых нет в файле импорта, они удаляются (чистка устаревших данных).

    2. Обновление Authorization Settings (updateAuthorizationSettings)
    Если у клиента включена авторизация (authorizationServicesEnabled), применяется стратегия "полной перезаписи":
    
        Удаление старого:
        Удаляются все существующие настройки в строгом порядке, чтобы избежать ошибок целостности данных:
    
        Сначала удаляются Resources (deleteAuthorizationResources).
        Затем удаляются Scopes (deleteAuthorizationScopes).
        Затем удаляются Policies (deleteAuthorizationPolicies).

        Импорт нового: 
        Вызывается clientResource.authorization().importSettings(exportSettings) для загрузки новой конфигурации целиком.

[назад к оглавлению](#оглавление)<br/><br/>

### Экспорт Authentication Flows из рабочей области

<span style="color:cornflowerblue">
GET / keycloak / migrate / realm / authentication / flow
</span>

##### <span style="color:green">Параметры запроса</span>

##### <span style="color:goldenrod">Query Params</span>

    realm (String): Название области (Realm), из которой производится экспорт.
    alias (String): Название (alias) экспортируемого потока.
    Поддерживает точное совпадение имени (например, browser).
    Поддерживает символ * для экспорта всех потоков верхнего уровня в Realm.
    Поддерживает список имен через запятую (реализовано во вспомогательном методе getRealmAuthenticationFlowList).

##### <span style="color:goldenrod">Response</span>

    1. В случае успеха (HTTP 200 OK)
    Возвращает объект FlowImportDto (в коде обозначен как exportFlowDto). Этот объект представляет собой полную структуру экспортированных данных и содержит:
    authenticationFlows: Список самих потоков аутентификации (включая вложенные под-потоки).
    authenticatorConfigs: Список конфигураций для шагов аутентификации (например, настройки OTP, Recaptcha и т.д.), которые используются в этих потоках.
    В теле ответа будет JSON-представление этого объекта.

    2. В случае ошибок (HTTP 4xx / 5xx)
    Возвращает текстовое сообщение об ошибке (String):
    HTTP 400 Bad Request:
        Если не переданы параметры realm или alias. Возвращает константу Constants.INVALID_REALM_OR_FLOW_NAME.
    HTTP 404 Not Found:
        Если Realm не найден: "Realm name = [$realm] not found".
        Если поток с указанным alias не найден: "Flow alias = [$alias] not found".
    HTTP 500 Internal Server Error (через метод writeErrorLoggerWithTextAndStatus):
        В случае возникновения исключения (Exception) во время сбора данных.

##### <span style="color:goldenrod">Описание работы метода</span>

    Алгоритм работы
    Поиск корневых потоков: Сервис ищет потоки верхнего уровня (isTopLevel = true), соответствующие переданному паттерну alias.

    Рекурсивный сбор данных: Для каждого найденного корневого потока вызывается метод collectAuthenticationSubFlows.

        Он сохраняет сам поток.
        Получает все шаги выполнения (executions) для этого потока.
        Если шаг имеет конфигурацию (authenticationConfig), она загружается и сохраняется.
        Если шаг является под-потоком (flowId не null), метод вызывает сам себя рекурсивно для этого ID.

    Сериализация: Собранная структура (CollectFlowDto) преобразуется в JSON

[назад к оглавлению](#оглавление)<br/><br/>

### Импорт Authentication Flows в другую рабочую область

<span style="color:cornflowerblue">
POST / keycloak / migrate / realm / authentication / flow
</span>

##### <span style="color:green">Параметры запроса</span>

##### <span style="color:goldenrod">Query Params</span>

    realm (String): Целевой Realm для импорта.
    stamp (String?): Строковая метка, которая добавляется к названию нового потока, если не задана, слоздается временная метка импорта. Используется для модификации имени потока (версионирования).

##### <span style="color:goldenrod">Body</span>

    flowImportDto (FlowImportDto): Объект данных, полученный на этапе экспорта.

##### <span style="color:goldenrod">Response</span>

    1. В случае успеха (HTTP 200 OK)
    Возвращает строку: "Flows created successfully"
    Это означает, что процесс прошел без ошибок, все потоки и их настройки были созданы или обновлены в Keycloak.

    2. В случае ошибок (HTTP 4xx / 5xx)
    Возвращает сообщение об ошибке (String):
    HTTP 400 Bad Request:
        Если переданный DTO пустой или в нем нет корневых потоков (findTopLevelFlow вернул пустоту). Возвращает константу Constants.INVALID_FLOW.
    HTTP 404 Not Found:
        Если указанный Realm не найден. Возвращает константу Constants.INVALID_REALM_NAME.
    HTTP 500 Internal Server Error:
        Если произошло исключение (Exception) в процессе создания. Возвращает лог ошибки через migrateService.writeErrorLoggerWithTextAndStatus.

##### <span style="color:goldenrod">Описание работы метода</span>

    Импорт позволяет воссоздать структуру потоков в целевом Realm. 
    Процесс включает автоматическое переименование для предотвращения конфликтов и полное восстановление иерархии шагов.

    Алгоритм работы

    Фильтрация: Из DTO выделяются только потоки верхнего уровня (findTopLevelFlow).
    Установка штампа: В сервисе сохраняется переданный stamp для дальнейшего использования при генерации имен.
    Итерация по корням: Для каждого корневого потока:

        Вызывается createAuthenticationFlow для создания самой сущности потока (контейнера).
        При создании к имени добавляется суффикс (например, browser 20231027 migrated), чтобы не затереть существующие системные потоки.

    Воссоздание окружения: Вызывается ключевой рекурсивный метод createAuthenticationFlowEnvironment.

    Внутренняя логика воссоздания (createAuthenticationFlowEnvironment)
    Этот приватный метод отвечает за наполнение созданного "пустого" потока содержимым.
    Перебор Executions: Метод проходит по списку шагов (authenticationExecutions) из импортируемого объекта.

    Тип шага:
        Обычный шаг (Authenticator): Вызывается createAuthenticationExecution.
            Создается шаг в Keycloak.
            Если у шага была конфигурация (authenticatorConfig), она ищется в DTO импорта, ей присваивается новое имя (с тем же штампом времени) и она привязывается к созданному шагу.

        Под-поток (Flow): Если шаг является ссылкой на другой поток (isAuthenticatorFlow = true):
            Система находит описание этого под-потока в DTO.
            Создает этот под-поток через createAuthenticationFlow.
            Создает execution, указывающий на этот новый под-поток.
            Рекурсия: Вызывает createAuthenticationFlowEnvironment для этого нового под-потока, чтобы наполнить его внутренности.

    Особенности реализации
    Версионирование имен (createFlowAliasTimeStamped)
    При импорте сервис никогда не перезаписывает существующие потоки с таким же именем. Вместо этого он модифицирует alias:
    Если поток импортируется впервые, к имени добавляется stamp + " migrated".
    Если поток уже был мигрирован ранее (имеет суффикс "migrated"), старый штамп заменяется на новый.
    Пример: browser -> browser 20251127 migrated.

    Работа с ID
    При экспорте ID сохраняются для связи объектов внутри JSON. При импорте:
    Все id принудительно устанавливаются в null.
    Новые ID генерируются Keycloak автоматически при создании (CreatedResponseUtil.getCreatedId).

    Консистентность данных
    Импорт происходит в два этапа для каждого узла дерева:
    Создание сущности (Flow или Execution).
    Наполнение конфигурацией (Config).
    Это гарантирует, что конфигурация привязывается только к успешно созданному элементу.


[назад к оглавлению](#оглавление)<br/><br/>
