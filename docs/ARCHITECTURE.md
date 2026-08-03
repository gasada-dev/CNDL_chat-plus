# Архитектура CNDL_chat+

Документ описывает фактическое состояние после PR 19. Мод является client-only Fabric entrypoint для Minecraft 26.2; все игровые API вызываются на client thread.

## Bootstrap и active template

`GasadaChatResponderClient` загружает совместимый `ResponderConfig`, создаёт services и регистрирует F8, client tick, ALLOW/CHAT/GAME events и HUD. `TemplateSelectionService` открывает repository, выбирает default template до обработки сообщений и при новом connection разрешает шаблон по фактическому `ServerData.ip`.

`ServerTemplateRuntime` публикует immutable `ActiveTemplateSnapshot`. Перед публикацией строятся `CompiledParserSettings`, `CompiledFilterSet` и `ReplyRuleMatcher`; message hot path не читает JSON и не компилирует regex. `TemplateSwitchCoordinator` сначала очищает:

- duplicate и own-message guards;
- friend lookup queue/pending response;
- friend presence, notices и HUD snapshot;
- periodic timers;
- compiled rules, filters и parsers;
- session overrides.

Если адрес не разрешён или template невозможно загрузить, runtime очищается: настройки другого сервера не применяются.

## Входящие сообщения

Порядок Fabric pipeline сохранён:

```text
FriendLookupManager interception
→ ChatVisibilityFilter
→ ChatResponderEngine
→ отображение сообщения
```

`ALLOW_CHAT`/`ALLOW_GAME` сначала дают `FriendLookupManager` скрыть служебный lookup block. Затем `ChatVisibilityFilter` применяет Discord toggle, Discord mute, explicit Minecraft sender mute и compiled muted words активного шаблона. Скрытое сообщение не доходит до responder callback.

`ChatResponderEngine` является координатором:

1. `OwnMessageGuard` отбрасывает собственный форматированный текст и echo в окне 5 секунд.
2. `DuplicateMessageGuard` отбрасывает одинаковый fingerprint в окне 400 мс.
3. `ChatChannelDetector` проверяет Discord, private markers, clan markers, global prefix, `(!)`, global markers и fallback `LOCAL` именно в этом порядке.
4. `ReplyCandidateBuilder` создаёт normalized candidates из content/displayed, prefixes и separators активного parser set.
5. `ReplyRuleMatcher` проходит compiled rules по порядку; первое enabled matching rule побеждает.
6. Ответ формируется для LOCAL/GLOBAL/CLAN/PRIVATE и передаётся `OutgoingChatService`.

`WildcardMatcher` считает специальным только `*`. Режим `FULL_MATCH` используется rules, `CONTAINS_MATCH` — muted words. Regex-метасимволы экранируются; case handling и пустые patterns сохраняют characterization semantics.

## Исходящие команды

`OutgoingChatService.MinecraftTransport` — единственное место вызовов Minecraft `sendChat`/`sendCommand`; оно повторно проверяет connection. `ServerCommandService` получает templates активного snapshot и валидирует аргументы непосредственно перед отправкой через `PlayerNameValidator`, `MessageValidator`, `AmountValidator`, `InputSanitizer` и `CommandTemplateValidator`.

Команды Vanilla-box находятся только в `ServerCommandSettings.vanillaBoxDefaults()`. При отсутствии command template fallback не применяется, отправка не выполняется. `FriendActionService` предоставляет UI/lookup friend actions, не собирая строки команд.

## Friends, lookup и HUD

`FriendLookupManager` принимает только друзей active snapshot, выдерживает delay 2,5 секунды и timeout 7 секунд, использует `FriendLookupParser`/compiled template patterns и отправляет lookup через command service. Очередь очищается при disconnect/switch. `last seen` обновляется в target template scope.

`FriendPresenceTracker` обновляется в client tick. Сохранены warmup 30 секунд, offline confirmation 5 секунд и notice 4 секунды. Tracker публикует `FriendHudSnapshot`; `FriendsHud.render` только рисует snapshot. Звук запускается из tick, не render. Reconnect/switch сбрасывает state до обработки нового списка.

## Периодические сообщения

`PeriodicMessageScheduler` читает до `PeriodicMessageConfig.MAX_PERIODIC_MESSAGES` (3) записей active snapshot. Первый send происходит после полного interval; disable, blank, invalid interval, disconnect, изменение text/interval и switch сбрасывают slot. Leading `/` означает command, остальное — chat; отправка идёт через `OutgoingChatService`.

## Templates, migration и import

`ServerTemplateRepository` атомарно пишет root/template JSON через sibling temp → move. `ServerTemplateManager` реализует create/copy/draft rename/address patterns/default/exact binding/delete protections. `ServerTemplateResolver` использует приоритет exact binding → exact pattern → wildcard → default → none.

`TemplateCatalogService` до начального выбора устанавливает отсутствующие bundled JSON из
`assets/gasada_chat_responder/server_templates/index.txt`. Совпадающие ID пропускаются, поэтому
обновление мода не перезаписывает пользовательский template. Внешние JSON размером до 1 MiB
загружаются только по команде UI из `config/gasada-chat-responder-template-imports`; перед
регистрацией проверяются ID/name, command placeholders и parser patterns.

`LegacyConfigToVanillaBoxMigration` до завершения новой схемы создаёт побайтовый backup старого config, сохраняет и перечитывает `server-templates/vanilla-box.json`, затем последним пишет root. Старый файл не удаляется и остаётся совместимым view Vanilla-box.

`TemplateImportService` строит отдельный `TemplateImportPreview`; source и persisted target до confirmation не меняются. Categories импортируются выборочно, списки поддерживают REPLACE/MERGE/SKIP, periodic ограничены тремя, existing last seen сохраняется без explicit overwrite, commands/parsers валидируются до apply.

## UI

`ResponderScreen` сохраняет четыре вкладки. Layout остаётся в screen, а mutations/save/suggestions/pagination/status/constants вынесены в tab controllers, `PlayerSuggestionProvider`, `Pagination`, `ScreenStatus` и `UiConstants`. Верхняя строка содержит cycle selector active template и кнопку настроек. Рассылки открывает намеренно невидимый `15×15` widget в `(0,0)` только на первой вкладке.

Подсказки friend actions получают templates из active `CommandSnapshot` и форматируют их
через `CommandTemplateDisplay`; названия `/w`, `/tpa`, pay/mail не зашиты в UI.

`TemplatesScreen`, `TemplateEditorScreen` и `TemplateImportScreen` используют draft/preview. Editor имеет страницы identity/address, всех именованных команд и Discord marker/name patterns. Runtime меняется только после успешного save или явного временного выбора. Активный/default/единственный template защищён от небезопасного удаления.

## Update checker

`UpdateChecker` использует один shared `HttpClient`, redirect policy `NEVER` и explicit `CheckState`. Async callback читает GitHub REST `releases/latest`, принимает только status 200, JSON/plain Content-Type, до 64 KiB строгого UTF-8 и публикует immutable DTO. Версия извлекается из numeric tag `vX.Y.Z`; выбирается только asset `CNDL_chat+-<version>.jar` с HTTPS URL точного release path репозитория. `UpdateVersion` отдельно сохраняет comparison characterization. Экран открывается только из client tick; автоматической установки нет.

## Threading и I/O invariants

- UI, connection/player list, chat/command send, HUD state и sound — client thread.
- HTTP — async; callback не открывает screen.
- JSON I/O не выполняется в message handler или render.
- HUD render не сохраняет, не запускает lookup/commands/sound и не вычисляет presence transitions.
- Persistent regex/wildcards компилируются при публикации snapshot, а не на каждом сообщении.

## Тестовые границы

JUnit 5 tests находятся в `src/test`. Characterization tests фиксируют wildcard/filter/channel/config/lookup/version/periodic legacy semantics; unit tests покрывают templates, migration, resolver/runtime resets, commands/parsers/filters, responder components, friends, scheduler, management/import UI services и update security. Minecraft rendering/Fabric event delivery остаются предметом ручных сценариев `docs/MANUAL_TESTS.md`.
