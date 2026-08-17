# Карта функций CNDL_chat+

| Функция | Основной владелец | Active/template data | Проверка |
|---|---|---|---|
| Bootstrap, F8, Fabric events | `GasadaChatResponderClient` | active runtime | Запуск, F8, connect/disconnect |
| Автовыбор template | `TemplateSelectionService`, `ServerTemplateResolver` | root bindings/patterns/default | exact, wildcard, default, unknown server |
| Template CRUD/UI | `ServerTemplateManager`, `TemplatesScreen`, `TemplateEditorScreen` | root + template files | create/copy/rename/commands/Discord/delete/default/bind/temp select |
| Bundled/external templates | `TemplateCatalogService` | JAR catalog + import folder | install once/no overwrite/validation/load button |
| Выборочный import | `TemplateImportService`, `TemplateImportScreen` | source/target template | preview, confirmation, REPLACE/MERGE/SKIP |
| Wildcard rules | `WildcardMatcher`, `ReplyRuleMatcher` | compiled active rules | exact/все позиции `*`/regex literals/first wins |
| Канал ответа | `ChatChannelDetector` | active prefixes/markers/parsers | Discord → private → clan → global → LOCAL |
| Candidates/normalization | `ReplyCandidateBuilder`, `ChatTextNormalizer` | active separators | prefixes, Unicode whitespace, punctuation |
| Echo и duplicate guards | `OwnMessageGuard`, `DuplicateMessageGuard` | runtime only | окна 5 с и 400 мс, reset on switch |
| Discord/muted visibility | `ChatVisibilityFilter`, `CompiledFilterSet` | active Discord/mutes/words | hidden message не активирует responder |
| Исходящий chat/command | `OutgoingChatService` | connection | единственные Minecraft API send calls |
| Именованные команды | `ServerCommandService` | active command templates | validators, missing command → no send |
| Friend actions | `FriendActionService`, `CommandTemplateDisplay` | active friends/commands | send validators + подсказки фактических templates |
| Friend lookup | `FriendLookupManager`, `FriendLookupParser`, `ServerLookupCoordinator` | active friends/patterns/last seen | delay/timeout/block interception/named fields/switch reset |
| Информация об игроке | `PlayerInfoScreen`, `PlayerInfoService`, `VanillaGameProfileClient`, `MarriageLookupManager` | active provider + commands/parsers + session cache | suggestions/manual refresh/API validation/lookup fields/marriage pages/stale reset |
| Friend presence/HUD | `FriendPresenceTracker`, `FriendsHud` | active friends/HUD/sound | warmup/offline confirm/notice/reconnect |
| Периодические сообщения | `PeriodicMessageScheduler`, `PeriodicMessageAccessScreen`, `PeriodicMessageScreen` | active periodic slots | password, полный interval, reset, chat vs command, max 3 |
| Main UI | `ResponderScreen` + tab controllers | compatible active view | четыре вкладки, template selector/settings, player info над друзьями, hidden 15×15 periodic hotspot |
| Legacy config/migration | `ConfigManager`, `ResponderConfig`, `LegacyConfigToVanillaBoxMigration`, `RootConfigSchemaMigration` | legacy + root + Vanilla-box | backup, sanitize, schema/ID migration, no repeat/no loss |
| Repository | `ServerTemplateRepository` | root/template JSON | atomic temp→move, isolation, corrupt-file failure |
| Update check | `UpdateChecker`, `UpdateVersion` | GitHub latest release | async/status/type/size/UTF-8/tag/asset URL/client tick |
| История чата | `ChatMessageStore`, `ChatHistoryStore`, `ChatHistoryCodec`, `ChatComponentMixin`, `ChatAccess` | `ResponderConfig` chatHistory*, per-server JSON | ring buffer/limit, atomic save/load, corrupt file, fileKey, mixin limit |
| Вкладки чата | `ChatTabController`, `ChatTabClassifier`, `ChatTabBar`, `ChatComponentFilterMixin`, `ChatScreenMixin` | `ResponderConfig` chatTabsEnabled, runtime identity map | classifier priority, unread counters, filter, remap |
| Timestamps | `ChatTimestamps`, `ChatTimestampMixin` | `ResponderConfig` chatTimestampsEnabled | prefix format, restored skip, disabled passthrough |
| Поиск по чату | `ChatSearchState`, `ChatScreenMixin`, `ChatComponentFilterMixin` | `ResponderConfig` chatSearchEnabled | trim/case-insensitive/filter AND active tab/reset |
| Контекстное меню чата | `ChatContextMenuController`, `ContextMenuBuilder`, `ChatMessageSenderExtractor`, `ChatMessageUnderMouseAccess` | active commands/parsers + `ResponderConfig` chatContextMenuEnabled | hit test/sender/actions/no-template fail-safe/drafts |

Подробные потоки описаны в `docs/ARCHITECTURE.md`, schema — в `docs/CONFIG.md` и `docs/SERVER_TEMPLATES.md`, команды — в `docs/SERVER_COMMANDS.md`.
