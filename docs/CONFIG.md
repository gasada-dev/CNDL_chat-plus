# Конфигурация CNDL_chat+

## Файлы

| Файл | Назначение |
|---|---|
| `.minecraft/config/gasada-chat-responder.json` | совместимый legacy view; для `Vanilla-box` сохраняется прежний JSON format |
| `.minecraft/config/gasada-chat-responder.legacy-backup.json` | побайтовый backup старого config перед первой миграцией |
| `.minecraft/config/server-templates.json` | `RootConfig`: schema, default, список templates, exact bindings |
| `.minecraft/config/server-templates/<id>.json` | один `ServerTemplate` на файл |
| `.minecraft/config/gasada-chat-responder-template-imports/*.json` | входящие пользовательские templates; читаются только по кнопке загрузки |

Имя legacy-файла и существующие поля не удалены. Все записи repository и legacy config используют UTF-8 и sibling `.tmp` → atomic move с replace fallback.

## RootConfig

- `schemaVersion`: текущая версия 1;
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
- `commands` (`ServerCommandSettings`);
- `parsers` (`ParserSettings`).

`ActiveTemplateSnapshot` является deep immutable copy. Runtime state (guards, lookup queue, presence/notices, timers и compiled data) в JSON не сохраняется.

Bundled templates находятся внутри JAR в
`assets/gasada_chat_responder/server_templates/`; `index.txt` перечисляет JSON-файлы,
которые разработчик хочет предустановить. При запуске отсутствующие ID регистрируются,
а существующие пользовательские файлы не перезаписываются. Внешний import ограничен
одним JSON-файлом до 1 MiB и проверяет структуру команд/parsers до сохранения.
Текущий bundled index содержит `vanilla-box.json` и `game.json`.

## Legacy ResponderConfig

Сохранены поля `enabled`, Discord toggle/mutes, `mutedWords`, `friends`, `friendLastSeen`, `friendHudEnabled`, `periodicMessages`, `rules`, prefixes и markers, а также старые одиночные `periodicEnabled`, `periodicMessage`, `periodicIntervalMinutes` для чтения миграции.

`ResponderConfig.sanitize()`:

1. восстанавливает null wrappers/collections/strings;
2. удаляет blank/null entries и dedup строковых списков без учёта регистра;
3. чистит неполный `friendLastSeen`;
4. мигрирует старую одиночную рассылку, затем очищает legacy fields;
5. гарантирует один slot при пустом списке и максимум `MAX_PERIODIC_MESSAGES` (3);
6. исправляет interval `<1` на 5;
7. восстанавливает обязательный global marker `(!)`;
8. восстанавливает rule fields/channel;
9. мигрирует точную старую пару стандартных rules в текущий default rule.

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

## Известные ограничения

- Повреждённый legacy JSON приводит к logged migration/load error и defaults, но byte-for-byte backup создаётся до parse и сохраняет исходный файл. Последующий UI save может заменить основной legacy JSON defaults, поэтому восстановление выполняется из backup вручную.
- Repository schema version пока равна 1 и не имеет downgrade path.
- Template editor редактирует identity/address metadata, все именованные серверные команды и Discord marker/name regex. Остальные категории редактируются существующими вкладками active view или импортируются.
- Ручное редактирование JSON может создать значения, которые UI не предлагает; commands/parsers повторно валидируются перед send/save/import, но не все display-only строки имеют общий length limit.
