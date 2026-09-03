# Серверные шаблоны

Server templates предотвращают смешивание commands, parsers, filters и friends серверов с разными форматами/командами. Runtime всегда работает с одним immutable active snapshot и не угадывает настройки по display name сервера. Legacy rules/periodic data хранятся только для миграции CNDL_toolkit.

## Модель и хранение

- `RootConfig` хранит schema version, default template, `ServerTemplateInfo` и exact address bindings.
- `ServerTemplate` хранит server-specific данные.
- `ServerTemplateRepository` читает/атомарно пишет `server-templates.json` и `server-templates/<id>.json`.
- `ServerTemplateManager` выполняет CRUD/metadata/default/bind operations.
- `ServerTemplateRuntime` публикует `ActiveTemplateSnapshot` и compiled derivatives.
- `TemplateCatalogService` устанавливает bundled templates и загружает внешние JSON.

Между templates изолированы:

- inert ordered reply rules, responder enabled, reply prefixes и periodic messages;
- channel global prefix и markers;
- muted words и Minecraft/Discord mute lists;
- Discord parser settings и mute list;
- friends и last seen;
- политика автоприёма телепорта и выбранные друзья;
- server command templates;
- Discord/channel/friend lookup parser patterns и separators.
- provider информации об игроке.

Старые JSON-поля `discordChatEnabled`, `friendHudEnabled` и `friendSoundEnabled` сохраняются
при load/save/copy как compatibility data, но больше не входят в active snapshot.

Automation bridge имеет exact preservation semantics во всех repository/deep-copy/import
paths: nullable collections, null elements/nested fields, order, count, messages и intervals
не sanitizes. Repository сериализует explicit nulls. Эти поля отсутствуют в
`ActiveTemplateSnapshot`, поэтому nullable values не попадают в runtime hot paths.

Глобальными остаются feature toggles чата, Discord-чата, HUD и звуков, MOD ID, F8 key mapping
(Minecraft controls), update-check runtime, UI theme, root schema/default/bindings и transient
application services. F9 принадлежит CNDL_toolkit. Queues/presence/compiled objects не являются config и сбрасываются на switch.

## Vanilla-box migration

Первый успешный migration совместимого `cndl-chat-plus.json` создаёт template ID `vanilla-box`, name `Vanilla-box`. При обновлении старый `gasada-chat-responder.json` сначала безопасно копируется в этот путь. Переносятся все server-specific legacy fields без потери rules/friends/blacklists/last seen/periodic data. Команды и parsers получают `ServerCommandSettings.vanillaBoxDefaults()` и `ParserSettings.vanillaBoxDefaults()`.

Порядок безопасности: byte-for-byte backup → read/visible-only sanitize → null-safe legacy copy → atomic template save → reread/equality check → root save → root reread. Старый config и его automation values не изменяются; повторная migration не создаёт duplicates.

## Выбор по адресу

`TemplateSelectionService` использует фактический `ServerData.ip`, а `ServerAddressNormalizer` приводит hostname/register/default port к canonical form. Приоритет:

```text
exact permanent binding
→ exact address pattern
→ most-specific wildcard subdomain pattern
→ default template
→ no active template
```

Ручной «Выбрать временно» действует до следующего connection change. «Привязать текущий адрес» сохраняет exact normalized binding. Display server name не используется как identity.

## Создание и редактирование

`TemplatesScreen` создаёт:

- пустой template;
- deep copy `Vanilla-box`;
- deep copy выбранного template.

`TemplateEditorScreen` редактирует display name/address patterns, provider информации об
игроке, lookup command, именованные команды CNDL_chat+, Discord regex и
именованные player-info regex через deep-copy draft. Каждая player-info regex имеет capture
group 1 и становится отдельной строкой экрана. Placeholders и regex проверяются до save. До
успешного repository save runtime не меняется. Можно выбрать default, временно активировать
или постоянно привязать текущий address. Delete требует повторного нажатия и запрещён для active, only и default template.

Server-specific списки выбранного active template редактируются вкладками `ResponderScreen`,
а команды и форматы открываются через `Настройка команд для сервера` в глобальном экране
настроек. Compatible `ResponderConfig` служит view и при save маршрутизируется в active template.
Non-Vanilla save не перезаписывает legacy Vanilla data; automation bridge остаётся скрытым и неизменным.

## Каталог и обмен готовыми шаблонами

- Для предустановки разработчик кладёт полные `ServerTemplate` JSON в
  `src/client/resources/assets/cndl_chat_plus/server_templates/` и описывает ресурс
  с официальными доменами в `catalog.json`. При первом запуске template копируется в
  repository; существующее содержимое никогда не перезаписывается.
- Текущий каталог содержит `vanilla-box.json` и `vanilla-game.json`. Их домены:
  `mc.vanilla-box.ru` и `mc.vanilla-game.ru`. Персональные категории `friends` и
  `friendLastSeen` во встроенном `vanilla-game` намеренно пусты.
- Bundled automation fields и legacy fixture не удаляются: это inert migration bridge,
  владельцем и потребителем которого является CNDL_toolkit.

Старый `vanilla-game` без явно сохранённого выбора provider один раз получает
`VANILLA_GAME_PUBLIC_API`; ручной выбор в editor помечается как явный и не заменяется.
- Пользователь кладёт полученные JSON в
  `.minecraft/config/cndl-chat-plus-template-imports/` и нажимает
  «Загрузить шаблоны из папки». Source-файлы остаются на месте, duplicate ID пропускаются.
- Импорт принимает только UTF-8 JSON до 1 MiB, безопасный ID, корректное имя, command
  placeholders и компилируемые parser patterns. Новый template сначала атомарно сохраняется,
  затем регистрируется в root; при ошибке root новая запись удаляется.

## Импорт

`TemplateImportOptions` выбирает категории:

- channels/markers;
- muted words/Minecraft/Discord lists;
- friends/last seen/teleport auto-accept;
- commands;
- parser patterns.
- provider информации об игроке и named lookup fields.

Списки поддерживают `REPLACE`, `MERGE`, `SKIP`. Friend mode применяется вместе с категорией
friends при `REPLACE`/`MERGE`, а список выбранных друзей использует тот же list mode. Merge строковых lists выполняет
case-insensitive dedup, где это допустимо. Existing target last seen не заменяется source value
без explicit overwrite. Commands и regex parsers валидируются до записи. Reply/periodic categories
отсутствуют; preview строится из deep copy target и сохраняет его inert automation fields.

`TemplateImportPreview` содержит proposed target и summary, но не пишет файлы. `TemplateImportScreen` требует preview и отдельное подтверждение. `TemplateImportService.apply` сохраняет только target; source не изменяется. Если target active, runtime перепубликуется после успешной записи.

## Runtime reset и hot path

При connect/manual switch сбрасываются lookup/pending queue, presence/HUD notices, compiled filters/parsers и temporary overrides. Изменение глобального feature toggle не переключает template и не сбрасывает другие сервисы. Snapshot deep immutable и не содержит automation bridge; JSON в incoming message/render не читается.

## Hardcode Vanilla-box

Vanilla-only strings локализованы в двух factories:

- `ServerCommandSettings.vanillaBoxDefaults()` — `/ignoreplayer`, `/clan lookup`, `/w`, `/pay`,
  `/call`, `/mail send`, `/ps add`, `/ps remove`, `/vm trusted add`, `/vm trusted remove`;
- `ParserSettings.vanillaBoxDefaults()` — Discord marker/name, channel separators, last seen/inactive/end/output/timestamp patterns.

Общий command/parser/filter runtime не содержит fallback на эти defaults. Статический legacy parser helper остаётся только для characterization compatibility tests.
Existing Vanilla-box получает отсутствующие nearby-player commands один раз; configured markers
после этого сохраняют пользовательское изменение или очищение, включая позднее добавленное
удаление из торговца.

## Ограничения

- Root schema version сейчас 3. Schema 1 безопасно мигрирует `game` в `vanilla-game`;
  конфликт двух ID сохраняет оба template без объединения.
- Встроенный `vanilla-game` задаёт `marry list {page}` и regex MarriageMaster. При
  обновлении они заполняют только отсутствующие поля. Runtime и editor разрешают marriage
  lookup только для точного ID `vanilla-game`; старые marriage-поля `vanilla-box` очищаются.
- UI metadata editor использует comma-separated address patterns; command templates хранятся без начального `/`. Legacy private reply prefix остаётся только в JSON bridge.
- Удаление после успешного root update удаляет отдельный template file; active/default protections предотвращают loss текущего selection.
- Minecraft client и реальные серверные formats требуют ручной проверки после изменения patterns/commands.
