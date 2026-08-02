# Карта функций CNDL_chat+

| Функция | Основной владелец | Active/template data | Проверка |
|---|---|---|---|
| Bootstrap, F8, Fabric events | `GasadaChatResponderClient` | active runtime | Запуск, F8, connect/disconnect |
| Автовыбор template | `TemplateSelectionService`, `ServerTemplateResolver` | root bindings/patterns/default | exact, wildcard, default, unknown server |
| Template CRUD/UI | `ServerTemplateManager`, `TemplatesScreen`, `TemplateEditorScreen` | root + template files | create/copy/rename/delete/default/bind/temp select |
| Выборочный import | `TemplateImportService`, `TemplateImportScreen` | source/target template | preview, confirmation, REPLACE/MERGE/SKIP |
| Wildcard rules | `WildcardMatcher`, `ReplyRuleMatcher` | compiled active rules | exact/все позиции `*`/regex literals/first wins |
| Канал ответа | `ChatChannelDetector` | active prefixes/markers/parsers | Discord → private → clan → global → LOCAL |
| Candidates/normalization | `ReplyCandidateBuilder`, `ChatTextNormalizer` | active separators | prefixes, Unicode whitespace, punctuation |
| Echo и duplicate guards | `OwnMessageGuard`, `DuplicateMessageGuard` | runtime only | окна 5 с и 400 мс, reset on switch |
| Discord/muted visibility | `ChatVisibilityFilter`, `CompiledFilterSet` | active Discord/mutes/words | hidden message не активирует responder |
| Исходящий chat/command | `OutgoingChatService` | connection | единственные Minecraft API send calls |
| Именованные команды | `ServerCommandService` | active command templates | validators, missing command → no send |
| Friend actions | `FriendActionService` | active friends/commands | `/w`, `/pay`, `/call`, `/mail send` |
| Friend lookup | `FriendLookupManager`, `FriendLookupParser` | active friends/patterns/last seen | delay/timeout/block interception/switch reset |
| Friend presence/HUD | `FriendPresenceTracker`, `FriendsHud` | active friends/HUD/sound | warmup/offline confirm/notice/reconnect |
| Периодические сообщения | `PeriodicMessageScheduler` | active periodic slots | полный interval, reset, chat vs command, max 3 |
| Main UI | `ResponderScreen` + tab controllers | compatible active view | четыре вкладки, visible «Рассылки», status/suggestions |
| Legacy config/migration | `ConfigManager`, `ResponderConfig`, `LegacyConfigToVanillaBoxMigration` | legacy + Vanilla-box | backup, sanitize, no repeat/no loss |
| Repository | `ServerTemplateRepository` | root/template JSON | atomic temp→move, isolation, corrupt-file failure |
| Update check | `UpdateChecker`, `UpdateVersion` | global runtime state | async/status/type/size/UTF-8/URL/version/client tick |

Подробные потоки описаны в `docs/ARCHITECTURE.md`, schema — в `docs/CONFIG.md` и `docs/SERVER_TEMPLATES.md`, команды — в `docs/SERVER_COMMANDS.md`.
