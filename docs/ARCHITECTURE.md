# Архитектура CNDL_chat+

Документ описывает текущее состояние проекта. Мод является client-only Fabric entrypoint для Minecraft 26.2; все игровые API вызываются на client thread.

## Bootstrap и active template

`CndlChatPlusClient` загружает совместимый `ResponderConfig`, создаёт services и регистрирует F8, client tick, ALLOW/CHAT/GAME events и HUD. `TemplateSelectionService` открывает repository, выбирает default template до обработки сообщений и при новом connection разрешает шаблон по фактическому `ServerData.ip`.

`ServerTemplateRuntime` публикует immutable `ActiveTemplateSnapshot`. Перед публикацией строятся `CompiledParserSettings`, `CompiledFilterSet` и `ReplyRuleMatcher`; message hot path не читает JSON и не компилирует regex. `TemplateSwitchCoordinator` сначала очищает:

- duplicate и own-message guards;
- friend lookup queue/pending response;
- friend presence, notices и HUD snapshot;
- periodic timers;
- compiled rules, filters и parsers.

Если resolver не находит template или выбранный template невозможно загрузить, runtime очищается:
настройки другого сервера не применяются. При отсутствии connection/address tick не меняет runtime.

## Входящие сообщения

Порядок Fabric pipeline сохранён:

```text
MarriageLookupManager / FriendLookupManager interception
→ ChatVisibilityFilter
→ ChatResponderEngine
→ отображение сообщения
```

`ALLOW_CHAT`/`ALLOW_GAME` сначала дают marriage/friend managers извлечь данные и скрыть
служебные lookup blocks. Затем `ChatVisibilityFilter` применяет Discord toggle, Discord
mute, explicit Minecraft sender mute и compiled muted words активного шаблона. Скрытое
сообщение не доходит до responder callback. При отсутствии active template или compiled
settings фильтр работает fail-open и показывает сообщение; responder без template ничего
не отправляет.

`ChatResponderEngine` является координатором:

1. Статическая проверка `OwnMessageGuard` отбрасывает собственный форматированный текст.
2. `DuplicateMessageGuard` отбрасывает одинаковый fingerprint в окне 400 мс.
3. `OwnMessageGuard` отбрасывает недавний echo в окне 5 секунд.
4. `ChatChannelDetector` проверяет Discord, private markers, clan markers, global prefix, `(!)`, global markers и fallback `LOCAL` именно в этом порядке.
5. `ReplyCandidateBuilder` создаёт normalized candidates из content/displayed, prefixes и separators активного parser set.
6. `ReplyRuleMatcher` проходит compiled rules по порядку; первое enabled matching rule побеждает.
7. Ответ формируется для LOCAL/GLOBAL/CLAN/PRIVATE и передаётся `OutgoingChatService`.

`WildcardMatcher` считает специальным только `*`. Режим `FULL_MATCH` используется rules, `CONTAINS_MATCH` — muted words. Regex-метасимволы экранируются; case handling и пустые patterns сохраняют characterization semantics.

## Исходящие команды

`OutgoingChatService.MinecraftTransport` — единственное место вызовов Minecraft `sendChat`/`sendCommand`; оно повторно проверяет connection. `ServerCommandService` получает templates активного snapshot и валидирует аргументы непосредственно перед отправкой через `PlayerNameValidator`, `MessageValidator`, `AmountValidator`, `InputSanitizer` и `CommandTemplateValidator`.

Команды Vanilla-box находятся только в `ServerCommandSettings.vanillaBoxDefaults()`. При отсутствии command template fallback не применяется, отправка не выполняется. `FriendActionService` предоставляет UI/lookup friend actions, не собирая строки команд.

## Friends, lookup и HUD

`FriendLookupManager` ставит в периодическую очередь только друзей active snapshot; ручной
player-info fallback принимает любой валидный Minecraft-ник. Manager выдерживает delay 2,5
секунды и timeout 7 секунд, использует `FriendLookupParser`/compiled template patterns и
отправляет lookup через command service. Очередь очищается при disconnect/switch. `last seen`
обновляется в target template scope.

`FriendPresenceTracker` обновляется в client tick. Сохранены warmup 30 секунд, offline confirmation 5 секунд и notice 4 секунды. Tracker публикует `FriendHudSnapshot`; `FriendsHud.render` только рисует snapshot. Звук запускается из tick, не render. Reconnect/switch сбрасывает state до обработки нового списка.

## Периодические сообщения

`PeriodicMessageScheduler` читает до `PeriodicMessageConfig.MAX_PERIODIC_MESSAGES` (3) записей active snapshot. Первый send происходит после полного interval; disable, blank, invalid interval, disconnect, изменение text/interval и switch сбрасывают slot. Leading `/` означает command, остальное — chat; отправка идёт через `OutgoingChatService`.

## Запрос телепорта

`TeleportRequestButton` сопоставляет system message с заранее скомпилированным
`teleportRequestPattern` active template. При совпадении HUD показывает кнопку на 60 секунд;
клик доступен в открытом чате и вызывает `ServerCommandService.acceptTeleport`. Timeout,
disconnect, template switch и успешный клик очищают запрос. Новый запрос один раз проигрывает
custom sound event, ссылающийся на встроенный `minecraft:entity/shulker/ambient4`, из client tick.
Без parser/command кнопка не появляется.

## Templates, migration и import

`ServerTemplateRepository` атомарно пишет root/template JSON через sibling temp → move. `ServerTemplateManager` реализует create/copy/draft rename/address patterns/default/exact binding/delete protections. `ServerTemplateResolver` использует приоритет exact binding → exact pattern → wildcard → default → none.

`RootConfigSchemaMigration` обновляет schema 1: безопасно переносит ID `game` в
`vanilla-game`, сохраняя template data/default/bindings и не объединяя конфликтующие ID.
`TemplateCatalogService` до начального выбора устанавливает отсутствующие bundled JSON из
`assets/cndl_chat_plus/server_templates/catalog.json`. Descriptor также добавляет
официальный домен существующему встроенному ID, не перезаписывая template. Внешние JSON размером до 1 MiB
загружаются только по команде UI из `config/cndl-chat-plus-template-imports`; перед
регистрацией проверяются ID/name, command placeholders и parser patterns.

`BrandPathMigration` до config load копирует старые branded config/import/history files в
`cndl-chat-plus-*` без удаления source или перезаписи target. Затем
`LegacyConfigToVanillaBoxMigration` до завершения новой схемы создаёт побайтовый backup
`cndl-chat-plus.json`, сохраняет и перечитывает `server-templates/vanilla-box.json`, затем
последним пишет root. Совместимый config остаётся view Vanilla-box.

`TemplateImportService` строит отдельный `TemplateImportPreview`; source и persisted target до confirmation не меняются. Categories импортируются выборочно, списки поддерживают REPLACE/MERGE/SKIP, periodic ограничены тремя, existing last seen сохраняется без explicit overwrite, commands/parsers валидируются до apply.

## UI

`ResponderScreen` сохраняет четыре вкладки. Часть mutations/save и UI helpers вынесена в tab
controllers, `PlayerSuggestionProvider`, `Pagination`, `ScreenStatus` и `UiConstants`; layout
и orchestration остаются в screen. Верхняя строка содержит cycle selector active template и
кнопку настроек. Намеренно невидимый `15×15` widget в `(0,0)` существует только на первой
вкладке и открывает `PeriodicMessageAccessScreen`; экран рассылок доступен после точного
ввода hardcoded-пароля `1239`.

Над вкладкой друзей находится кнопка `Информация об игроке`. `PlayerInfoScreen` получает
online suggestions из текущего connection и загружает данные только по `Обновить`.
`PlayerInfoService` хранит session cache и отбрасывает ответы старой runtime generation.
`VanillaGameProfileClient` обращается только к фиксированному HTTPS host/path без redirects,
проверяет status, Content-Type, UTF-8 и размер body. При отказе API запрос ставится в общую
очередь `FriendLookupManager`; parser сначала извлекает named `playerInfoPatterns`, затем
скрывает lookup block и передаёт собранные поля экрану. disconnect и switch завершают/очищают очередь. UI намеренно
не показывает building score, placeholder скрытых контактов и pwarp без достоверного источника.

Если успешный профиль VanillaGame содержит `marry: null`, `PlayerInfoService` передаёт
запрос в `MarriageLookupManager`. Он использует command `marriageList {page}` и compiled
patterns active template, последовательно просматривает до 100 страниц и обогащает уже
загруженный профиль. `ServerLookupCoordinator` исключает одновременную отправку friend
lookup и marriage lookup. Оба состояния сбрасываются при disconnect/template switch.

Подсказки friend actions получают templates из active `CommandSnapshot` и форматируют их
через `CommandTemplateDisplay`; названия `/w`, `/tpa`, pay/mail не зашиты в UI.

`TemplatesScreen`, `TemplateEditorScreen` и `TemplateImportScreen` используют draft/preview. Editor имеет страницы identity/address, всех именованных команд и Discord marker/name patterns. Runtime меняется только после успешного save или явного временного выбора. Активный/default/единственный template защищён от небезопасного удаления.

## Update checker

`UpdateChecker` использует один shared `HttpClient`, redirect policy `NEVER` и explicit `CheckState`. Async callback читает GitHub REST `releases/latest`, принимает только status 200, JSON/plain Content-Type, до 64 KiB строгого UTF-8 и публикует immutable DTO. Версия извлекается из numeric tag `vX.Y.Z`; release обязан содержать точные assets `CNDL_chat+-<version>-mc1.21.11.jar` и `CNDL_chat+-<version>-mc26.2.jar` с HTTPS URL точного release path репозитория. `UpdateVersion` отдельно сохраняет comparison characterization. Экран с отдельной кнопкой для каждой версии открывается только из client tick; автоматической установки нет.

## История чата

`ChatMessageStore` хранит входящие сообщения (timestamp + JSON `Component`) в ring buffer с
лимитом из `ResponderConfig.chatHistoryLimit`. Запись идёт из `ClientReceiveMessageEvents`
CHAT/GAME после `ChatVisibilityFilter`: скрытые и overlay-сообщения не сохраняются.
Сериализация — `ChatHistoryCodec` через `ComponentSerialization.CODEC` с registry access
текущего уровня/connection; MC API изолирован от store.

`ChatComponentMixin` поднимает vanilla-лимит 100 в `addMessageToQueue`/`addMessageToDisplayQueue`
до configured limit (`@ModifyConstant`, `require=0`: при смене байткода Mojang лимит молча
остаётся vanilla вместо падения). `ChatHistoryStore` пишет per-server JSON в
`config/cndl-chat-plus-chat-history/<fileKey>.json` (имя файла — нормализованный адрес
с sanitization) через sibling temp → atomic move. Save — на disconnect, load и вставка в
`ChatComponent` — на join до прихода новых сообщений; повреждённый файл fail-open. Singleplayer
и direct connect без `ServerData` не сохраняются. Доступ к чату различается между target'ами
и вынесен в per-target `ChatAccess` (`src/targets/`).

## Вкладки чата и timestamps

`ChatTabClassifier` классифицирует входящее сообщение в `ChatTab` (ALL/GLOBAL/LOCAL/CLAN/
PRIVATE/DISCORD/SYSTEM): Discord parser active template имеет приоритет, затем
`ChatChannelDetector` по маркерам (маркеры важнее типа пакета — серверы могут слать чат
системными сообщениями), и только сообщения без маркеров становятся SYSTEM (GAME-события) или
LOCAL. `ChatTabController` хранит active tab,
unread counters (инкремент, если chat закрыт или tab не активна; сброс при выборе/открытии) и
identity map `Component → fromGame` (cap 16384, при переполнении чистится). Канал всегда
вычисляется из текста + флага fromGame, поэтому счётчик и фильтр согласованы; в 26.2 флаг
берётся из `GuiMessage.source()` (PLAYER/SYSTEM_*), в 1.21.11 — из identity map.
`ChatComponentFilterMixin` (per-target, класс `GuiMessage`
различается) отменяет `addMessageToDisplayQueue` для сообщений вне active tab; переключение
вкладки вызывает private `refreshTrimmedMessages` через `@Invoker` в `ChatComponentMixin`.

`ChatScreenMixin` (per-target: `render` в 1.21.11, `extractRenderState` в 26.2) рисует
`ChatTabBar` над верхней строкой чата (позиция от private `getHeight` через `@Invoker` и
vanilla bottom margin 40) и
перехватывает ЛКМ по вкладкам. `ChatTimestampMixin`
(per-target descriptor `addMessage`) подставляет серый префикс `[HH:mm]` через
`@ModifyVariable`; префикс создаёт новый `Component`, поэтому `ChatTimestamps` вызывает
`ChatTabController.remapComponent`, чтобы перенести флаг fromGame на prefixed instance. Восстановленные
из истории сообщения получают префикс с исходным timestamp и заносятся в skip-set, чтобы
mixin не добавил второй. Все новые injector'ы используют `require=0`, кроме `@Invoker`
refreshTrimmedMessages (проверен в байткоде обоих target'ов).

`ChatSearchState` хранит нормализованный lowercase query только пока открыт `ChatScreen`.
Ctrl+F показывает native `EditBox`; изменение строки вызывает `refreshTrimmedMessages`, а
`ChatComponentFilterMixin` применяет search predicate вместе с active tab через AND. Пустой
query и закрытие поиска возвращают все сообщения active tab; unread counters поиск не меняет.

ПКМ по видимой строке получает `ChatMessageTarget` через per-target
`ChatMessageUnderMouseAccess`: 26.2 использует parent из `GuiMessage.Line`, 1.21.11 сопоставляет
группу wrapped lines с visible `allMessages`. `ChatMessageSenderExtractor` применяет Discord
parser и separators active template; SYSTEM не получает player actions. `ContextMenuBuilder`
показывает только доступные validated command actions. Перед copy и sender extraction
`ChatMessageTextSanitizer` удаляет synthetic accessibility labels вида `[Player head]`.
Copy работает локально; ЛС/pay/mail
подставляют draft в chat input, call/ignore идут через `ServerCommandService`, friend add сохраняет
active template, player info открывает предзаполненный `PlayerInfoScreen`. Без active template
командные действия отсутствуют.

## Threading и I/O invariants

- UI, connection/player list, chat/command send, HUD state и sound — client thread.
- HTTP — async; callback не открывает screen.
- JSON I/O не выполняется в message handler или render.
- HUD render не сохраняет, не запускает lookup/commands/sound и не вычисляет presence transitions.
- Persistent regex/wildcards компилируются при публикации snapshot, а не на каждом сообщении.

## Тестовые границы

JUnit 5 tests находятся в `src/test`. Characterization tests фиксируют wildcard/filter/channel/config/lookup/version/periodic legacy semantics; unit tests покрывают templates, migration, resolver/runtime resets, commands/parsers/filters, responder components, friends, scheduler, management/import UI services и update security. Minecraft rendering/Fabric event delivery остаются предметом ручных сценариев `docs/MANUAL_TESTS.md`.
