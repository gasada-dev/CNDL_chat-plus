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

Каждый template изолирует:

- `responderEnabled`, ordered `rules`;
- `globalPrefix`, `clanReplyPrefix`, `privateReplyCommand`;
- global/clan/private markers;
- `mutedWords`, `mutedMinecraftPlayers`;
- `discordChatEnabled`, `discordMutedPlayers`;
- `friends`, `friendLastSeen`, `friendHudEnabled`, `friendSoundEnabled`;
- до трёх `periodicMessages`;
- `commands` (`ServerCommandSettings`), включая `marriageList` с `{page}` и `acceptTeleport` без placeholders;
- `parsers` (`ParserSettings`), включая `playerInfoPatterns`: имя видимого поля →
  regex с capture group 1 для server lookup.
- `playerInfo.provider`: `NONE` или `VANILLA_GAME_PUBLIC_API`;
- только для `vanilla-game`: `playerInfo.marriageLookupConfigured` и parser-поля `marriageEntryPattern`,
  `marriagePagePattern`, `marriageEmptyPattern`. Первые два regex содержат по две
  capture groups: ники пары и current/max page соответственно.
- `teleportRequestPattern`: regex запроса телепорта с ником в capture group 1;
  `teleportRequestConfigured` защищает пользовательское отключение от повторной установки bundled default.

`ActiveTemplateSnapshot` является deep immutable copy. Runtime state (guards, lookup queue, presence/notices, timers и compiled data) в JSON не сохраняется.

Bundled templates находятся внутри JAR в
`assets/cndl_chat_plus/server_templates/`; `catalog.json` связывает JSON-файлы
с официальными address patterns. При запуске отсутствующие ID регистрируются, а для
существующих встроенных ID добавляются только отсутствующие официальные домены и
ещё не настроенные новые marriage/teleport-поля без перезаписи пользовательских значений. Внешний import ограничен
одним JSON-файлом до 1 MiB и проверяет структуру команд/parsers до сохранения.
Текущий bundled catalog содержит `vanilla-box.json` и `vanilla-game.json`.
Bundled `vanilla-game.json` содержит серверные команды, parser settings и публичный
провайдер информации об игроке, но не содержит
персональных друзей или last seen. Уже существующий пользовательский template с тем
же ID не перезаписывается при обновлении JAR.

## Legacy ResponderConfig

Сохранены поля `enabled`, Discord toggle/mutes, `mutedWords`, `friends`, `friendLastSeen`, `friendHudEnabled`, `periodicMessages`, `rules`, prefixes и markers, а также старые одиночные `periodicEnabled`, `periodicMessage`, `periodicIntervalMinutes` для чтения миграции.

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

1. восстанавливает null wrappers/collections/strings;
2. удаляет blank/null entries и dedup строковых списков без учёта регистра;
3. чистит неполный `friendLastSeen`;
4. мигрирует старую одиночную рассылку, затем очищает legacy fields;
5. гарантирует один slot при пустом списке и максимум `MAX_PERIODIC_MESSAGES` (3);
6. исправляет interval `<1` на 5;
7. восстанавливает обязательный global marker `(!)`;
8. восстанавливает rule fields/channel;
9. мигрирует точную старую пару стандартных rules в текущий default rule;
10. восстанавливает null `chatHistoryEnabled`/`chatHistoryPersist`/`chatHistoryLimit` и clamps
    limit к `[MIN_CHAT_HISTORY_LIMIT, MAX_CHAT_HISTORY_LIMIT]` (100–16384);
11. восстанавливает null `chatTabsEnabled`/`chatTimestampsEnabled`/`chatSearchEnabled`/
    `chatContextMenuEnabled`.

При выбранном не-`Vanilla-box` template `ConfigManager.save` маршрутизирует compatible UI view только в файл active template и не перезаписывает legacy Vanilla-box. Для `Vanilla-box` старый JSON сохраняется и те же server-specific fields синхронизируются в template, не заменяя commands/parsers.

## Безопасная миграция

`LegacyConfigToVanillaBoxMigration` выполняется один раз:

1. проверяет, что migration ещё не завершена;
2. создаёт и побайтово проверяет backup старого config;
3. читает/sanitize старый JSON;
4. переносит все server-specific поля в `vanilla-box`;
5. атомарно сохраняет template и перечитывает его для equality check;
6. последним сохраняет root с registered/default `vanilla-box`;
7. перечитывает root и только после проверки считает migration завершённой.

Старый файл не удаляется. Повторный запуск не добавляет duplicates. Ошибка одного template-файла не удаляет root/backup/другие templates.

## Defaults Vanilla-box

Сохранены прежние `!`, `/.`, `/r`, markers, `ChatChannel` values, first-rule-wins, wildcard semantics, три periodic slots и команды из `ServerCommandSettings.vanillaBoxDefaults()`. Parser hardcode Vanilla-box хранится в `ParserSettings.vanillaBoxDefaults()`, а общий runtime fallback не используется.

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
- Template editor редактирует identity/address metadata, все именованные серверные команды и Discord marker/name regex. Остальные категории редактируются существующими вкладками active view или импортируются.
- Ручное редактирование JSON может создать значения, которые UI не предлагает; commands/parsers повторно валидируются перед send/save/import, но не все display-only строки имеют общий length limit.
