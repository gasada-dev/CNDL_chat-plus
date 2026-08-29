# Конфигурация CNDL_chat+

## Файлы

| Файл | Назначение |
|---|---|
| `.minecraft/config/cndl-chat-plus.json` | совместимый view; для `Vanilla-box` сохраняется прежний JSON format |
| `.minecraft/config/cndl-chat-plus.legacy-backup.json` | побайтовый backup config перед первой миграцией в `Vanilla-box` |
| `.minecraft/config/server-templates.json` | `RootConfig`: schema, default, список templates, exact bindings |
| `.minecraft/config/server-templates/<id>.json` | один `ServerTemplate` на файл |
| `.minecraft/config/cndl-chat-plus-template-imports/*.json` | входящие пользовательские templates; читаются только по кнопке загрузки |
| `.minecraft/config/cndl-chat-plus-chat-history/<server>.json` | сохранённая история чата per server; пишется на disconnect, читается на join |

До чтения config старые `gasada-chat-responder.json`,
`gasada-chat-responder-template-imports/` и `gasada-chat-responder-chat-history/` копируются
в новые пути через sibling `.tmp` → atomic move. Исходники не удаляются, существующие новые
файлы не перезаписываются, скопированные bytes проверяются. JSON fields не меняются.

## RootConfig

- `schemaVersion`: текущая версия 3;
- `defaultTemplateId`: template для адресов без binding/pattern;
- `templates[]`: `id`, display `name`, `addressPatterns[]`;
- `serverBindings`: нормализованный exact address → template ID.

Адрес нормализуется к lowercase ASCII hostname и explicit port (по умолчанию `25565`). Resolver: exact binding → exact pattern → наиболее специфичный wildcard subdomain → default → безопасное отсутствие template.

## ServerTemplate

Каждый template изолирует рабочие настройки CNDL_chat+ и inert migration bridge:

- inert `responderEnabled`, ordered `rules`, `clanReplyPrefix`, `privateReplyCommand`;
- `globalPrefix` для классификации вкладок;
- global/clan/private markers;
- `mutedWords`, `mutedMinecraftPlayers`;
- `discordChatEnabled`, `discordMutedPlayers`;
- `friends`, `friendLastSeen`, `friendHudEnabled`, `friendSoundEnabled`;
- `teleportAutoAcceptMode`: `OFF`, `EVERYONE`, `FRIENDS` или `SELECTED_FRIENDS`, default `OFF`;
- `teleportAutoAcceptFriends`: выбранное подмножество `friends` для `SELECTED_FRIENDS`;
- inert `periodicMessages`;
- `commands` (`ServerCommandSettings`), включая `marriageList` с `{page}`, `acceptTeleport`
  без placeholders и Vanilla-box `protectionAdd`/`protectionRemove`/
  `traderTrustedAdd`/`traderTrustedRemove` с `{player}`;
- `commands.nearbyPlayerCommandsConfigured` защищает ручное отключение Alt+ПКМ-команд от
  повторной установки bundled defaults;
- `commands.traderTrustedRemoveConfigured` отдельно защищает добавленную позднее команду
  удаления из торговца;
- `parsers` (`ParserSettings`), включая `playerInfoPatterns`: имя видимого поля →
  regex с capture group 1 для server lookup.
- `playerInfo.provider`: `NONE` или `VANILLA_GAME_PUBLIC_API`;
- только для `vanilla-game`: `playerInfo.marriageLookupConfigured` и parser-поля `marriageEntryPattern`,
  `marriagePagePattern`, `marriageEmptyPattern`. Первые два regex содержат по две
  capture groups: ники пары и current/max page соответственно.
- `teleportRequestPattern`: regex запроса телепорта с ником в capture group 1;
  `teleportRequestConfigured` защищает пользовательское отключение от повторной установки bundled default.

`ActiveTemplateSnapshot` является deep immutable copy только runtime-настроек CNDL_chat+ и не содержит automation bridge. Runtime state (lookup queue, presence/notices и compiled data) в JSON не сохраняется.

`ServerTemplate.sanitize()` восстанавливает только runtime fields CNDL_chat+. Он не меняет
`responderEnabled`, nullable `rules` и nested/null entries/order, `clanReplyPrefix`,
`privateReplyCommand` или nullable `periodicMessages` с любым count/null/message/interval.
`deepCopy()` сохраняет эти значения точно и копирует non-null DTO независимо. Repository
использует Gson `serializeNulls`, поэтому explicit null survives save/load; runtime snapshot
не dereferences automation fields.

Bundled templates находятся внутри JAR в
`assets/cndl_chat_plus/server_templates/`; `catalog.json` связывает JSON-файлы
с официальными address patterns. При запуске отсутствующие ID регистрируются, а для
существующих встроенных ID добавляются только отсутствующие официальные домены и
ещё не настроенные новые marriage/teleport/nearby-player поля без перезаписи пользовательских значений. Внешний import ограничен
одним JSON-файлом до 1 MiB и проверяет структуру команд/parsers до сохранения.
Текущий bundled catalog содержит `vanilla-box.json` и `vanilla-game.json`.
Bundled `vanilla-game.json` содержит серверные команды, parser settings и публичный
провайдер информации об игроке, но не содержит
персональных друзей или last seen. Уже существующий пользовательский template с тем
же ID не перезаписывается при обновлении JAR.
Automation-поля в обоих bundled JSON намеренно сохранены как inert migration bridge;
их читает и мигрирует CNDL_toolkit, CNDL_chat+ их не исполняет.

## Legacy ResponderConfig

Сохранены поля `enabled`, Discord toggle/mutes, `mutedWords`, `friends`, `friendLastSeen`, `friendHudEnabled`, `periodicMessages`, `rules`, prefixes и markers, а также старые одиночные `periodicEnabled`, `periodicMessage`, `periodicIntervalMinutes` для чтения миграции. Automation-поля не исполняются и не показываются CNDL_chat+ 0.8.0; их владелец и потребитель миграции — CNDL_toolkit.

Глобальные настройки истории чата (не template-specific):

- `chatHistoryEnabled` (default `true`) — запись истории и повышенный лимит чата;
- `chatHistoryPersist` (default `true`) — сохранение истории между сессиями per server;
- `chatHistoryLimit` (default 1000, clamp 100–16384) — лимит ring buffer и отображаемой истории;
- `chatTabsEnabled` (default `true`) — вкладки чата (Все/Глобал/Локал/Клан/ЛС/Discord/Система)
  с непрочитанными счётчиками в открытом чате;
- `chatTimestampsEnabled` (default `true`) — серый префикс `[HH:mm]` у каждого сообщения.
- `chatSearchEnabled` (default `true`) — фильтр открытого чата по Ctrl+F без учёта регистра.
- `chatContextMenuEnabled` (default `true`) — interaction menu по ПКМ на видимой строке чата.

`ResponderConfig.sanitize()`:

1. восстанавливает null wrappers/collections только для видимых Discord/filter/friends полей и
   default `OFF` для автоприёма телепорта;
2. удаляет blank/null entries и dedup этих строковых списков без учёта регистра;
3. чистит неполный `friendLastSeen` и удаляет из `teleportAutoAcceptFriends` отсутствующих друзей;
4. восстанавливает `globalPrefix`, channel markers и обязательный global marker `(!)`;
5. восстанавливает null `chatHistoryEnabled`/`chatHistoryPersist`/`chatHistoryLimit` и clamps
    limit к `[MIN_CHAT_HISTORY_LIMIT, MAX_CHAT_HISTORY_LIMIT]` (100–16384);
6. восстанавливает null `chatTabsEnabled`/`chatTimestampsEnabled`/`chatSearchEnabled`/
    `chatContextMenuEnabled`.

`sanitize()` не изменяет inert bridge: `enabled`, `rules` и nested values/order,
`periodicMessages` и entries/order/count/intervals, legacy periodic singleton,
`clanReplyPrefix` и `privateReplyCommand`. Null automation collections и explicit null nested
values сохраняются. Старые default-rule и singleton-periodic migrations из общего sanitize удалены.

При выбранном не-`Vanilla-box` template `ConfigManager.save` маршрутизирует compatible UI view только в файл active template и не перезаписывает legacy Vanilla-box. Обычный save применяет к target только visible global channel/filter/Discord/friends/HUD/teleport auto-accept fields; automation bridge, commands/parsers и friend sound не заменяются. Для `Vanilla-box` compatible JSON сохраняет explicit null через Gson `serializeNulls`, а template обновляется тем же visible-only helper. До save template selection заполняет compatible view, поэтому скрытые automation-поля сохраняются вместе с изменением каналов, фильтров или друзей.

## Безопасная миграция

`LegacyConfigToVanillaBoxMigration` выполняется один раз:

1. проверяет, что migration ещё не завершена;
2. создаёт и побайтово проверяет backup старого config;
3. читает JSON и sanitizes только visible fields, не меняя automation source;
4. переносит все server-specific поля в `vanilla-box`: non-empty periodic list имеет приоритет,
   иначе legacy singleton создаёт одну запись при заданном `periodicEnabled`;
5. атомарно сохраняет template и перечитывает его для equality check;
6. последним сохраняет root с registered/default `vanilla-box`;
7. перечитывает root и только после проверки считает migration завершённой.

Старый файл не удаляется. Повторный запуск не добавляет duplicates. Ошибка одного template-файла не удаляет root/backup/другие templates.

## Defaults Vanilla-box

Сохранены прежние `!`, `/.`, `/r`, markers, `ChatChannel` values, rules и три periodic slots как persisted migration data, а также команды из `ServerCommandSettings.vanillaBoxDefaults()`. CNDL_chat+ использует только global prefix/markers для классификации чата и не исполняет rules/periodic data. Parser hardcode Vanilla-box хранится в `ParserSettings.vanillaBoxDefaults()`, а общий runtime fallback не используется.

`playerInfoPatterns` Vanilla-box извлекают клан, ранг, статус, КПД/KDR, убийства,
нейтральных, смерти, дату вступления, прошлые кланы и тип убийства. У существующего
встроенного template эти patterns добавляются один раз, только если пользователь ещё не
сохранял собственный набор.

## Миграция schema 1 → 3

Старый ID `game` копируется в `vanilla-game`, перечитывается для проверки, затем root
атомарно обновляет metadata/default/bindings. Файл `game.json` удаляется только после
успешной записи root. Если оба ID уже существуют, ни один template не перезаписывается;
миграция сохраняет оба и пишет предупреждение.

## Известные ограничения

- Повреждённый legacy JSON приводит к logged migration/load error и defaults, но byte-for-byte backup создаётся до parse и сохраняет исходный файл. Последующий UI save может заменить основной legacy JSON defaults, поэтому восстановление выполняется из backup вручную.
- Repository schema version равна 3 и не имеет downgrade path.
- Template editor редактирует identity/address metadata, именованные серверные команды CNDL_chat+ и Discord marker/name regex. Automation categories не редактируются и не импортируются, но сохраняются при load/save/deep copy.
- Ручное редактирование JSON может создать значения, которые UI не предлагает; commands/parsers повторно валидируются перед send/save/import, но не все display-only строки имеют общий length limit.
