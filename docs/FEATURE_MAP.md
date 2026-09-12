# Карта функций CNDL_chat+

| Функция | Основной владелец | Active/template data | Проверка |
|---|---|---|---|
| Bootstrap, F8, Fabric events | `CndlChatPlusClient` | active runtime | запуск, F8 Friends default, Player Info + `⚙`, connect/disconnect |
| Vanilla-box connection gate | `VanillaBoxConnectionGate`, `CndlChatPlusClient`, `OutgoingChatService` | normalized allowed connection или inactive | apex/true subdomain/port/normalization, malformed and lookalike deny, join/disconnect reset, queued send recheck (`VanillaBoxConnectionGateTest`, `OutgoingChatServiceTest`) |
| VnbxBridge transport (только 26.2) | `VnbxBridgeClient`, `VnbxPlayerRelationsResult`, target `PlatformBridgeNetworking` | transient payloads и correlated requests текущей сессии | protocol/type/size/relations validation, correlation/timeout, handshake, disconnect reset |
| Connection template selection | `TemplateSelectionService` | normalized gate-approved address + `vanilla-box` | `connect()` сохраняет address scope и выбирает только `vanilla-box`; denied address останавливается gate |
| Resolver metadata/template-management | `ServerTemplateResolver`, `ServerTemplateManager` | persisted root bindings/patterns/default | exact, wildcard, default, отсутствие template для compatibility metadata; resolver не вызывается при connection и не активирует мод вне gate |
| Template CRUD/UI | `ServerTemplateManager`, `TemplatesScreen`, `TemplateEditorScreen` | root + template files | create/copy/rename/commands/Discord/delete/default/bind/temp select |
| Bundled/external templates | `TemplateCatalogService` | JAR catalog + import folder | install once/no overwrite/validation/load button |
| Выборочный import | `TemplateImportService`, `TemplateImportScreen` | source/target template | preview, confirmation, REPLACE/MERGE/SKIP |
| Wildcard filters | `WildcardMatcher` | compiled muted words | все позиции `*`/regex literals/case handling |
| Каналы чата | `ChatChannelDetector` | active global prefix/markers/parsers | Discord → private → clan → global → LOCAL |
| Normalization | `ChatTextNormalizer` | chat text | Unicode whitespace и lowercase matching |
| Discord/muted visibility | `ChatVisibilityFilter`, `CompiledFilterSet` | global Discord toggle + active mutes/words | hidden message не попадает в history/tabs |
| Исходящий chat/command | `OutgoingChatService` | connection | единственные Minecraft API send calls |
| Чат-бинды | `ChatBindService`, `ChatBind`, `SettingsScreen`, `ResponderConfig` | global `ResponderConfig.chatBinds` | defaults/sanitize/save, capture, one send per press, GUI/world gating |
| Именованные команды | `ServerCommandService` | active command templates | validators, missing command → no send |
| Friend actions | `FriendActionService`, `CommandTemplateDisplay` | active friends/commands | send validators + подсказки фактических templates |
| Friend lookup | `FriendLookupManager`, `FriendLookupParser`, `ServerLookupCoordinator`, `ResponderScreen` | active friends/patterns/last seen | delay/timeout/block interception/named fields/switch reset, trailing partial batch, UI map-equality last-seen refresh (`FriendLookupManagerTest`, `ResponderScreenTest`) |
| Информация об игроке | `PlayerInfoScreen`, `PlayerInfoService`, `VnbxBridgeClient`, target `PlatformBridgeNetworking` | bridge relations + commands/parsers + session cache | suggestions/manual refresh/bridge relations/lookup fields/stale reset |
| Friend presence/HUD | `FriendPresenceTracker`, `FriendsHud` | active friends + global HUD/sound | warmup/offline confirm/notice/reconnect |
| HUD брака и меню действий | `MarriageHudController`, `MarriageHud`, `MarriageMenuScreen` | self profile текущих connection/generation + active marriage commands | one-shot bridge refresh/stale result/bounds/action order/fail-closed send |
| Main UI | `ResponderScreen`, `SettingsScreen`, `HelpScreen` + tab controllers | global toggles + compatible active view | две равные вкладки, Friends default, Player Info + справа `⚙`, settings/help, server commands button |
| Legacy config/migration | `ConfigManager`, `BrandPathMigration`, `ResponderConfig`, `LegacyConfigToVanillaBoxMigration`, `RootConfigSchemaMigration` | legacy + root + Vanilla-box | brand paths, backup, inert automation bridge, schema migration, no repeat/no loss |
| Repository | `ServerTemplateRepository` | root/template JSON | atomic temp→move, isolation, corrupt-file failure |
| Update check | `UpdateChecker`, `UpdateVersion`, `UpdateAvailableScreen` | GitHub latest release + `UPDATE_NOTES.md` | async/status/type/size/UTF-8/tag/asset/notes URL/client tick/window preview |
| История чата | `ChatMessageStore`, `ChatHistoryStore`, `ChatHistoryCodec`, `ChatComponentMixin`, `ChatAccess`, `ResponderConfig` | global chatHistory*, per-server JSON | ring buffer default 3000, custom limit preservation, atomic save/load, corrupt file, fileKey, mixin limit (`ResponderConfigTest`) |
| Вкладки чата | `ChatTabController`, `ChatTabClassifier`, `ChatTabBar`, `ChatComponentFilterMixin`, `ChatScreenMixin` | `ResponderConfig` chatTabsEnabled, runtime identity map | classifier priority, unread counters, filter, remap |
| Timestamps | `ChatTimestamps`, `ChatTimestampMixin` | `ResponderConfig` chatTimestampsEnabled | prefix format, restored skip, disabled passthrough |
| Повторы сообщений | `ChatDuplicateCollapser`, `ChatDuplicateAccess`, target `ChatComponentFilterMixin` | global enable + transient previous Component/count | consecutive-only/source/style/reset/history/timestamp/unread |
| Поиск по чату | `ChatSearchState`, `ChatScreenMixin`, `ChatComponentFilterMixin` | `ResponderConfig` chatSearchEnabled | trim/case-insensitive/filter AND active tab/reset |
| Контекстное меню чата | `ChatContextMenuController`, `ContextMenuBuilder`, `ChatMessageSenderExtractor`, `ChatMessageUnderMouseAccess` | active commands/parsers + `ResponderConfig` chatContextMenuEnabled | hit test/sender/actions/no-template fail-safe/drafts |
| Chat Alerts | `ChatAlertService`, `ChatAlertRuleCompiler`, `ChatAlertHud`, `ChatAlertsScreen` | global `ResponderConfig.chatAlerts*` + transient HUD | matching/channel/aggregation/config sanitize, visible pipeline вручную |
| Закладки сообщений | `ChatBookmarkStore`, `ChatContextMenuController`, `ChatBookmarksScreen` | отдельный per-server JSON + session runtime | CRUD, text-only edit preserving metadata/order, limits, atomic persistence, corrupt JSON, isolation, context action (`ChatBookmarkStoreTest`) |
| Alt+ПКМ по игроку | `NearbyPlayerMenuScreen`, `ServerCommandService` | exact Vanilla-box + protection/trader commands | modifier/player hit/gating/validation/send |
| Принятие запроса ТП | `TeleportRequestButton`, `ServerCommandService` | active `acceptTeleport` + `teleportRequestPattern` + auto-accept mode/friends | match/policy/timeout/switch/click/send |

Подробные потоки описаны в `docs/ARCHITECTURE.md`, schema — в `docs/CONFIG.md` и `docs/SERVER_TEMPLATES.md`, команды — в `docs/SERVER_COMMANDS.md`.
