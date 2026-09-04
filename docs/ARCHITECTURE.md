# Архитектура CNDL_chat+

Документ описывает текущее состояние проекта. Мод является client-only Fabric entrypoint для Minecraft 26.2; все игровые API вызываются на client thread.

## Bootstrap и active template

`CndlChatPlusClient` загружает совместимый `ResponderConfig`, создаёт services и регистрирует F8, client tick, ALLOW/CHAT/GAME events и HUD. F8 открывает менеджер чата; F9 и automation принадлежат CNDL_toolkit. `TemplateSelectionService` открывает repository, выбирает default template до обработки сообщений и при новом connection разрешает шаблон по фактическому `ServerData.ip`.

`ServerTemplateRuntime` публикует immutable `ActiveTemplateSnapshot`. Перед публикацией строятся `CompiledParserSettings` и `CompiledFilterSet`; message hot path не читает JSON и не компилирует regex. Automation-поля остаются только в persisted `ServerTemplate` и не входят в runtime snapshot. `TemplateSwitchCoordinator` сначала очищает:

- friend lookup queue/pending response;
- friend presence, notices и HUD snapshot;
- compiled filters и parsers.

Если resolver не находит template или выбранный template невозможно загрузить, runtime очищается:
настройки другого сервера не применяются. При отсутствии connection/address tick не меняет runtime.

## Входящие сообщения

Порядок Fabric pipeline сохранён:

```text
MarriageLookupManager / FriendLookupManager interception
→ ChatVisibilityFilter
→ ChatAlertService
→ ChatDuplicateCollapser / teleport handling
→ history/tabs
→ отображение сообщения
```

`ALLOW_CHAT`/`ALLOW_GAME` сначала дают marriage/friend managers извлечь данные и скрыть
служебные lookup blocks. Затем `ChatVisibilityFilter` применяет глобальный Discord toggle, Discord
mute, explicit Minecraft sender mute и compiled muted words активного шаблона. Скрытое
сообщение не записывается в историю и вкладки. При отсутствии active template или compiled
settings фильтр работает fail-open и показывает сообщение. CNDL_chat+ не запускает
auto-reply callback и не отправляет сообщения по legacy rules.

`ChatAlertService` получает только принятые фильтром CHAT/GAME events до объединения повторов.
Он использует общую классификацию `ChatTabClassifier` и очищенный через
`ChatMessageTextSanitizer` текст тела сообщения после распознанного sender-разделителя; ник
отправителя не участвует в matching. `ChatAlertRuleCompiler` при load/save публикует immutable
список TEXT/WILDCARD/REGEX matchers; message hot path regex не компилирует. Одно событие
создаёт не больше одного HUD notice и
одного sound action независимо от числа совпавших правил. Очередь HUD ограничена тремя
уведомлениями с исходным текстом сообщения на четыре секунды; render только рисует snapshot. Join/disconnect очищают
очередь и pending sounds. Восстановление history идёт напрямую в `ChatComponent`
и alerts не запускает. Alert-конфигурация глобальна и не входит в active template.

`ChatDuplicateCollapser` после фильтра сравнивает отображаемый текст, видимое форматирование и source type только
с непосредственно предыдущей отображённой строкой. Последовательные duplicates отменяются
через Fabric `ALLOW_CHAT`/`ALLOW_GAME`; target-specific `ChatDuplicateAccess` заменяет первый
`GuiMessage`, сохраняя signature/source/tag. Счётчик `xN`, timestamp последнего повтора и
последняя persisted history entry обновляются без нового unread. Любая другая player/system
или client-side строка сбрасывает серию. GAME side effects, включая TP request, выполняются и
для отменённого duplicate; overlay messages не участвуют. При disconnect/template switch
runtime state очищается. Глобальное отключение функции также очищает текущую серию, не влияя
на timestamps, вкладки, поиск или history.

`ChatChannelDetector` проверяет Discord, private markers, clan markers, global prefix, `(!)`,
global markers и fallback `LOCAL` именно в этом порядке. Его используют вкладки и context UI.
`WildcardMatcher` считает специальным только `*`; `CONTAINS_MATCH` используется muted words.

## Исходящие команды

`OutgoingChatService.MinecraftTransport` — единственное место вызовов Minecraft `sendChat`/`sendCommand`; оно повторно проверяет connection. Composition root создаёт сервис с no-op recorder, потому что echo guard удалён. `ServerCommandService` получает templates активного snapshot и валидирует аргументы непосредственно перед отправкой через `PlayerNameValidator`, `MessageValidator`, `AmountValidator`, `InputSanitizer` и `CommandTemplateValidator`.

Команды Vanilla-box находятся только в `ServerCommandSettings.vanillaBoxDefaults()`. При отсутствии command template fallback не применяется, отправка не выполняется. `FriendActionService` предоставляет UI/lookup friend actions, не собирая строки команд.

`UseEntityCallback` перехватывает `Alt+ПКМ` по `Player` только при точном active ID
`vanilla-box`, main hand и закрытом GUI. Обычное взаимодействие отменяется без server packet,
после чего `NearbyPlayerMenuScreen` предлагает `/ps add`, `/ps remove`, `/vm trusted add`
и `/vm trusted remove`.
Ник повторно валидируется, а команды отправляются только через `ServerCommandService`.

## Friends, lookup и HUD

`FriendLookupManager` ставит в FIFO-очередь только друзей active snapshot; обход автоматически
начинается через 30 секунд после подключения или раньше при открытии friends tab. Manager
проверяет по пять игроков с delay 10 секунд между завершёнными ответами и паузой 60 секунд
между группами. Timeout равен 15 секундам; background lookup без данных один раз повторяется
после 60-секундной паузы. Ручной player-info fallback принимает любой валидный Minecraft-ник,
идёт перед оставшейся background-очередью, но соблюдает общий cooldown. Parser использует
compiled template patterns, отправка идёт через command service. Очереди, batch/retry и
автозапуск очищаются при disconnect/switch. `last seen` обновляется в target template scope.

`FriendPresenceTracker` обновляется в client tick. Сохранены warmup 30 секунд, offline confirmation 5 секунд и notice 4 секунды. Tracker публикует `FriendHudSnapshot`; `FriendsHud.render` только рисует snapshot. Глобальные HUD и звук включаются независимо; звук запускается из tick, не render. Reconnect/switch сбрасывает state до обработки нового списка.

## Запрос телепорта

`TeleportRequestButton` сопоставляет system message с заранее скомпилированным
`teleportRequestPattern` active template. Политика `teleportAutoAcceptMode` принимает запросы
от всех, друзей или выбранных друзей через `ServerCommandService.acceptTeleport`. При успешном
автоприёме HUD и звук не создаются; при несовпадении или ошибке отправки HUD показывает кнопку
на 60 секунд. Клик доступен в открытом чате. Timeout, disconnect, template switch и успешный
клик очищают запрос. Ручной запрос при включённом глобальном переключателе один раз проигрывает custom sound event, ссылающийся на
встроенный `minecraft:entity/shulker/ambient4`, из client tick. Без parser/command кнопка не появляется.

## Templates, migration и import

`ServerTemplateRepository` атомарно пишет root/template JSON через sibling temp → move и сериализует explicit nulls для exact automation bridge round-trip. `ServerTemplateManager` реализует create/copy/draft rename/address patterns/default/exact binding/delete protections. `ServerTemplateResolver` использует приоритет exact binding → exact pattern → wildcard → default → none.

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

`TemplateImportService` строит отдельный `TemplateImportPreview`; source и persisted target до confirmation не меняются. Categories импортируются выборочно, списки поддерживают REPLACE/MERGE/SKIP, existing last seen сохраняется без explicit overwrite, commands/parsers валидируются до apply. Reply/periodic categories отсутствуют; deep copy target сохраняет inert automation fields без изменений.

## UI

`ResponderScreen` содержит две равные вкладки: чёрный список и друзья. Часть mutations/save и UI helpers вынесена в tab
controllers, `PlayerSuggestionProvider`, `Pagination`, `ScreenStatus` и `UiConstants`; layout
и orchestration остаются в screen. Верхняя строка содержит cycle selector active template,
кнопки глобальных настроек и многостраничной подсказки. `SettingsScreen` независимо переключает
вкладки, поиск, timestamps, повторы, context menu, Discord, HUD и два звука; серверные шаблоны
открываются из него кнопкой `Настройка команд для сервера`. Rules tab, periodic hotspot и password UI отсутствуют.

Внизу вкладки друзей находится cycle автоприёма телепорта. Режим выбранных друзей показывает
персональный переключатель только после выбора друга из списка. Настройки изолированы active template.

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

`TemplatesScreen`, `TemplateEditorScreen` и `TemplateImportScreen` используют draft/preview. Editor имеет страницы identity/address, именованных команд CNDL_chat+, каналов с Discord marker/name patterns и player info; private auto-reply prefix не показывается. Runtime меняется только после успешного save или явного временного выбора. Активный/default/единственный template защищён от небезопасного удаления.

## Update checker

`UpdateChecker` использует один shared `HttpClient`, redirect policy `NEVER` и explicit `CheckState`. Async callback читает GitHub REST `releases/latest`, принимает только status 200, JSON/plain Content-Type, до 64 KiB строгого UTF-8 и публикует immutable DTO. Версия извлекается из numeric tag `vX.Y.Z`; release обязан содержать точные assets `CNDL_chat+-<version>-mc1.21.11.jar` и `CNDL_chat+-<version>-mc26.2.jar` с HTTPS URL точного release path репозитория. Release body до 4 KiB формируется из `UPDATE_NOTES.md`; окно показывает помещающуюся часть и строит подтверждаемую ссылку на этот файл точного тега. `UpdateVersion` отдельно сохраняет comparison characterization. Экран с отдельной кнопкой для каждой версии открывается только из client tick; автоматической установки нет.

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

Пункт `Сохранить в закладки` доступен для любого найденного context target независимо от
sender и active template. `ChatMessageTextSanitizer` удаляет собственный timestamp и Chat
Heads label из bookmark text. `ChatBookmarkStore` держит отдельный список текущего connection
и атомарно пишет каждую явную mutation в
`config/cndl-chat-plus-chat-bookmarks/<fileKey>.json`. Join загружает scope по тому же
нормализованному server address, что history; disconnect сохраняет и очищает runtime list.
Bookmarks не входят в `ChatMessageStore`, template snapshot/import и не зависят от history
toggles. Singleplayer без `ServerData` хранится только до disconnect. Кнопка над панелью
вкладок открытого чата открывает список: новые записи сверху, copy только text, delete и отдельное
подтверждения перед очисткой всех.

## VnbxBridge transport

Minecraft 26.2 target регистрирует двунаправленный raw UTF-8 JSON payload `vnbx:bridge`.
`PlatformBridgeNetworking` изолирует Fabric API, `VnbxBridgeClient` проверяет protocol/type/16 KiB limit
и хранит последние сообщения только до disconnect. Transport не меняет UI, config или active template;
server-specific adapters остаются на стороне VnbxBridge. Target 1.21.11 содержит только no-op facade.

## Threading и I/O invariants

- UI, connection/player list, chat/command send, HUD state и sound — client thread.
- HTTP — async; callback не открывает screen.
- JSON I/O не выполняется в message handler или render.
- Bookmark I/O выполняется только по явному context/UI action и на connection lifecycle.
- HUD render не сохраняет, не запускает lookup/commands/sound и не вычисляет presence transitions.
- Persistent regex/wildcards компилируются при публикации snapshot, а не на каждом сообщении.

## Тестовые границы

JUnit 5 tests находятся в `src/test`. Characterization tests фиксируют wildcard/filter/channel/config/lookup/version и legacy migration semantics; persistence/copy/import tests доказывают сохранение inert automation bridge. Unit tests покрывают templates, migration, resolver/runtime resets, commands/parsers/filters, friends, management/import UI services и update security. Minecraft rendering/Fabric event delivery остаются предметом ручных сценариев `docs/MANUAL_TESTS.md`.
