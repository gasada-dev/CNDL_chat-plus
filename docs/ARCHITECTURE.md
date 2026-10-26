# Архитектура CNDL_chat+

CNDL_chat+ является client-only Fabric модом для Minecraft 1.21.11, 26.2 и 26.3. Игровой UI,
connection, player list, send, HUD и sound выполняются на client thread. F8 открывает менеджер
чата, F9 и automation принадлежат CNDL_toolkit.

## Bootstrap, gate и runtime

`CndlChatPlusClient` загружает `ResponderConfig`, выполняет `VanillaBoxStorageMigration`,
создаёт сервисы и регистрирует Fabric events. `VanillaBoxConnectionGate` является единственной
границей server activation. Он принимает любой корректный multiplayer `ServerData.ip`, включая
hostname, IPv4 и IPv6 с optional port, и нормализует его через `ServerAddressNormalizer`.
Malformed input, singleplayer и connection без `ServerData` отклоняются без DNS lookup.

При корректном multiplayer join `VanillaBoxConfigStore` загружает только
`server-templates/vanilla-box.json`, а `VanillaBoxRuntime` публикует immutable
`VanillaBoxSnapshot`. Нормализованный адрес используется только для history/bookmark scope.
Он не участвует в выборе config. Invalid join, disconnect, failed migration, missing config после
`storageVersion: 1` или compilation failure очищают runtime, gate, очереди, HUD и transient state.
Это fail-inactive поведение, без defaults и fallback.

Snapshot содержит deep immutable settings, generation и заранее скомпилированные parser/filter
артефакты. `RuntimeResetCoordinator` очищает lookup queues, presence и transient UI state при
activation и clear. Generation является эпохой активации: `updateLastSeen` публикует новый snapshot
с прежней generation без очистки очереди и инвалидации запросов. Message и
render hot paths не читают файлы, не делают HTTP и не компилируют regex.

## Хранение и migration

`cndl-chat-plus.json` хранит global visible settings и compatible view. `VanillaBoxConfig` в
`server-templates/vanilla-box.json` является единственной server-specific authority. Существующий
корректный файл побеждает совместимое представление. `ServerCommandSettings.vanillaBoxDefaults()`
и `ParserSettings.vanillaBoxDefaults()` применяются только при первой инициализации. Bundled
catalog и JSON-конфигурации в JAR отсутствуют.

`ResponderConfig.storageVersion` nullable: absent, `null` и `0` запускают переход. Migration
сначала сохраняет и перечитывает Vanilla-box config, затем архивирует retired candidates в
`cndl-chat-plus-retired-server-support-v1/files/<relative-path>`. `manifest.json` содержит
отсортированные `path`, `size` и lowercase SHA-256. Archive проверяет raw bytes до удаления.
Совпадающий неизменяемый archive поддерживает resume после прерывания. Только manifest-listed
`server-templates.json`, non-Vanilla JSON из `server-templates/` и
`cndl-chat-plus-template-imports/**` удаляются после verification. `storageVersion: 1` пишется
последним.

Legacy backup, branded config sources, branded imports, history и bookmarks остаются. Automation
bridge, включая nullable `rules`, `periodicMessages`, prefixes и nested values, не исполняется и
не нормализуется CNDL_chat+, но сохраняется для CNDL_toolkit.

## Сообщения, команды и privacy

Входящий pipeline имеет порядок: friend lookup interception, visibility filter, Chat Alerts,
duplicate collapse и teleport handling, затем history/tabs и vanilla display. Скрытые сообщения
не записываются. При inactive runtime фильтр fail-open, то есть vanilla message остаётся видимым.
Global Chat Alerts обрабатывают только принятые сообщения.

Встроенный `ChatTab` остаётся identity для classifier, history и Chat Alerts. Пользовательские
вкладки являются только view overlays: каждая выбирает один или несколько фиксированных
`ChatTabSource`, а `ChatTabController` сопоставляет результат существующего classifier. Источник
`VOICE` дополнительно определяется по фиксированному `(Войс)`; произвольные пользовательские
фильтры не исполняются. Одно сообщение может попасть в несколько пользовательских вкладок.
При восстановлении истории сохранённый источник имеет приоритет над target-specific GUI source,
поэтому `LOCAL` и `SYSTEM` не меняются местами на Minecraft 26.2. Пользовательские ID не
становятся identity истории, закладок или alerts.
`hiddenBuiltInTabs` скрывает только кнопку встроенной вкладки, а не меняет её классификацию.

`OutgoingChatService.MinecraftTransport` является единственным вызовом Minecraft send API. Он
повторно проверяет gate, connection и generation перед transport. `ServerCommandService` строит
только команды Vanilla-box, валидирует placeholders и аргументы непосредственно перед send.
F7, `\`, friend actions, teleport и marriage actions не работают вне active runtime. Private
messages, email, reply payloads и amounts не логируются.

`CndlChatPlusClient` регистрирует custom-tab route в shared `ClientSendMessageEvents.ALLOW_CHAT`.
Непустой текст без начального `/` в активной пользовательской вкладке с префиксом отправляется
через `OutgoingChatService` как команда с этим префиксом. Явная slash-команда не меняется и
остаётся vanilla command path. Если runtime inactive или shared send проверка отклоняет команду,
маршрут поглощает это сообщение без vanilla fallback.
`SendResult` различает `QUEUED`, `SENT` и `FAILED`: успешный возврат при постановке в очередь
не гарантирует исполнение. Completion сообщает результат transport или отмену по подключению/эпохе.
`SENT` означает вызов клиентского send API, а не подтверждение доставки сервером. Для custom-tab
ошибка выводится локальным notice, видимым независимо от фильтра вкладки, с действиями восстановления
slash-команды и копирования исходного текста; автоматической повторной отправки нет.

## Пользовательские функции

`ResponderScreen` содержит вкладки «Чёрный список» и «Друзья». В заголовке находятся `?`,
`Информация об игроке` и увеличенная `⚙`. `SettingsScreen` открывает настройки вкладок, а в
нижней строке размещает `Chat Alerts` рядом с переходом к «Биндам». Настройки UI, истории,
Discord, HUD, sound, alerts, binds и пользовательских вкладок глобальны. Нет экранов server
templates, import, catalog или настройки fixed commands/parsers.

Friends, filter settings, channel markers, teleport policy и commands берутся из одной
Vanilla-box config. `FriendLookupManager` и `PlayerInfoService` используют compiled parsers.
На Minecraft 26.2 и 26.3 `VnbxBridgeClient` принимает bounded raw UTF-8 JSON `vnbx:bridge`; payload
не попадает в config/history или log. На 1.21.11 bridge сразу unavailable. Minecraft 26.3 хранит
бинды в прежних GLFW-числах JSON и переводит их в SDL-коды только на runtime-границе.

`ChatHistoryStore` и `ChatBookmarkStore` хранят данные отдельно по нормализованному multiplayer
адресу через atomic temporary file и move. Они не создают scope в singleplayer или invalid
connection. Bookmarks независимы от history toggles.
Повреждённая/частично некорректная история сохраняется побайтово в backup перед заменой;
при невозможности backup исходный файл не заменяется. Несохранённые bookmarks удерживаются в
памяти по server key и повторно записываются при возвращении к scope; замена store при invalid join
не выполняется. Этот pending state не переживает завершение процесса.

`TeleportRequestButton` хранит очередь по порядку поступления, показывает до трёх карточек сверху
и принимает выбранного игрока через `ServerCommandService` с аргументом ника. Таймаут каждого
запроса — 60 секунд от получения, включая скрытые; повторный запрос того же игрока обновляет
карточку на прежней позиции. Карточка удаляется после `SENT`, а не после `QUEUED`; ошибка оставляет её.
Ручные `/tpaccept` и серверное подтверждение без ника с очередью не синхронизируются.

## Поддерживаемые границы

Тесты JUnit 5 находятся в `src/test/java`. Они проверяют gate, fixed config store, archive,
storage migration и её idempotence, runtime snapshots и resets, commands, parsers, filters,
friends, history, bookmarks и VnbxBridge. Ручные проверки обоих Minecraft targets описаны в
[MANUAL_TESTS.md](MANUAL_TESTS.md).
