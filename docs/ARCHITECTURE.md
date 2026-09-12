# Архитектура CNDL_chat+

CNDL_chat+ является client-only Fabric модом для Minecraft 1.21.11 и 26.2. Игровой UI,
connection, player list, send, HUD и sound выполняются на client thread. F8 открывает менеджер
чата, F9 и automation принадлежат CNDL_toolkit.

## Bootstrap, gate и runtime

`CndlChatPlusClient` загружает `ResponderConfig`, выполняет `VanillaBoxStorageMigration`,
создаёт сервисы и регистрирует Fabric events. `VanillaBoxConnectionGate` является единственной
границей server activation. Он принимает только `ServerData.ip` с hostname `vanilla-box.ru` или
настоящим поддоменом, с корректным optional port. Регистр и одна trailing dot нормализуются.
Lookalike, unrelated hostname, IP, malformed input, singleplayer и connection без `ServerData`
отклоняются без DNS lookup.

При разрешённом join `VanillaBoxConfigStore` загружает только
`server-templates/vanilla-box.json`, а `VanillaBoxRuntime` публикует immutable
`VanillaBoxSnapshot`. Нормализованный адрес используется только для history/bookmark scope.
Он не участвует в выборе config. Denied join, disconnect, failed migration, missing config после
`storageVersion: 1` или compilation failure очищают runtime, gate, очереди, HUD и transient state.
Это fail-inactive поведение, без defaults и fallback.

Snapshot содержит deep immutable settings, generation и заранее скомпилированные parser/filter
артефакты. `RuntimeResetCoordinator` очищает lookup queues, presence и transient UI state при
activation и clear. `updateLastSeen` публикует новую generation без очистки очереди. Message и
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

`OutgoingChatService.MinecraftTransport` является единственным вызовом Minecraft send API. Он
повторно проверяет gate, connection и generation перед transport. `ServerCommandService` строит
только команды Vanilla-box, валидирует placeholders и аргументы непосредственно перед send.
F7, `\`, friend actions, teleport и marriage actions не работают вне active runtime. Private
messages, email, reply payloads и amounts не логируются.

## Пользовательские функции

`ResponderScreen` содержит вкладки «Чёрный список» и «Друзья». В заголовке находятся `?`,
`Информация об игроке` и `⚙`. Настройки UI, истории, Discord, HUD, sound, alerts и binds
глобальны. Нет экранов server templates, editor, import, catalog или настройки команд/parsers.

Friends, filter settings, channel markers, teleport policy и commands берутся из одной
Vanilla-box config. `FriendLookupManager` и `PlayerInfoService` используют compiled parsers.
На Minecraft 26.2 `VnbxBridgeClient` принимает bounded raw UTF-8 JSON `vnbx:bridge`; payload
не попадает в config/history или log. На 1.21.11 bridge сразу unavailable.

`ChatHistoryStore` и `ChatBookmarkStore` хранят данные отдельно по нормализованному разрешённому
адресу через atomic temporary file и move. Они не создают scope в singleplayer или denied
connection. Bookmarks независимы от history toggles.

## Поддерживаемые границы

Тесты JUnit 5 находятся в `src/test/java`. Они проверяют gate, fixed config store, archive,
storage migration и её idempotence, runtime snapshots и resets, commands, parsers, filters,
friends, history, bookmarks и VnbxBridge. Ручные проверки обоих Minecraft targets описаны в
[MANUAL_TESTS.md](MANUAL_TESTS.md).
