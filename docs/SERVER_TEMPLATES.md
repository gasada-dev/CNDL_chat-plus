# Серверные шаблоны

Server templates предотвращают смешивание rules, commands, parsers, filters, friends и timers серверов с разными форматами/командами. Runtime всегда работает с одним immutable active snapshot и не угадывает настройки по display name сервера.

## Модель и хранение

- `RootConfig` хранит schema version, default template, `ServerTemplateInfo` и exact address bindings.
- `ServerTemplate` хранит server-specific данные.
- `ServerTemplateRepository` читает/атомарно пишет `server-templates.json` и `server-templates/<id>.json`.
- `ServerTemplateManager` выполняет CRUD/metadata/default/bind operations.
- `ServerTemplateRuntime` публикует `ActiveTemplateSnapshot` и compiled derivatives.
- `TemplateCatalogService` устанавливает bundled templates и загружает внешние JSON.

Между templates изолированы:

- ordered reply rules и responder enabled;
- channel prefixes/markers и reply command;
- muted words и Minecraft/Discord mute lists;
- Discord toggle/settings;
- friends и last seen;
- friend HUD/sound;
- periodic messages;
- server command templates;
- Discord/channel/friend lookup parser patterns и separators.

Глобальными остаются MOD ID, F8 key mapping (Minecraft controls), update-check runtime, UI theme, root schema/default/bindings и transient application services. Guards/queues/presence/timers/compiled objects не являются config и сбрасываются на switch.

## Vanilla-box migration

Первый успешный migration старого `gasada-chat-responder.json` создаёт template ID `vanilla-box`, name `Vanilla-box`. Переносятся все server-specific legacy fields без потери rules/friends/blacklists/last seen/periodic data. Команды и parsers получают `ServerCommandSettings.vanillaBoxDefaults()` и `ParserSettings.vanillaBoxDefaults()`.

Порядок безопасности: byte-for-byte backup → read/sanitize → atomic template save → reread/equality check → root save → root reread. Старый config не удаляется и повторная migration не создаёт duplicates.

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

`TemplateEditorScreen` редактирует display name/address patterns, все шесть именованных команд, private reply prefix и Discord marker/name regex через deep-copy draft. Placeholders и regex проверяются до save. До успешного repository save runtime не меняется. Можно выбрать default, временно активировать или постоянно привязать текущий address. Delete требует повторного нажатия и запрещён для active, only и default template.

Основные server settings выбранного active template продолжают редактироваться четырьмя вкладками `ResponderScreen`; compatible `ResponderConfig` служит view и при save маршрутизируется в active template. Non-Vanilla save не перезаписывает legacy Vanilla data.

## Каталог и обмен готовыми шаблонами

- Для предустановки разработчик кладёт полные `ServerTemplate` JSON в
  `src/client/resources/assets/gasada_chat_responder/server_templates/` и добавляет имена
  файлов в `index.txt`. При первом запуске они копируются в repository; существующий ID
  всегда выигрывает и не перезаписывается обновлением JAR.
- В версии 0.5 каталог содержит `vanilla-box.json` и `game.json` с ID `game`, display
  name `mc.vanilla-game.ru`, его командами и parser settings. Персональные категории
  `friends` и `friendLastSeen` во встроенном `game` намеренно пусты для новых установок.
- Пользователь кладёт полученные JSON в
  `.minecraft/config/gasada-chat-responder-template-imports/` и нажимает
  «Загрузить шаблоны из папки». Source-файлы остаются на месте, duplicate ID пропускаются.
- Импорт принимает только UTF-8 JSON до 1 MiB, безопасный ID, корректное имя, command
  placeholders и компилируемые parser patterns. Новый template сначала атомарно сохраняется,
  затем регистрируется в root; при ошибке root новая запись удаляется.

## Импорт

`TemplateImportOptions` выбирает категории:

- reply rules;
- channels/markers;
- muted words/Minecraft/Discord lists;
- Discord settings;
- friends/last seen/HUD/sound;
- periodic messages;
- commands;
- parser patterns.

Списки поддерживают `REPLACE`, `MERGE`, `SKIP`. Merge строковых lists выполняет case-insensitive dedup, где это допустимо; rules учитывают trigger/response/channel/enabled. Periodic result обрезается до трёх. Existing target last seen не заменяется source value без explicit overwrite. Commands и regex parsers валидируются до записи.

`TemplateImportPreview` содержит proposed target и summary, но не пишет файлы. `TemplateImportScreen` требует preview и отдельное подтверждение. `TemplateImportService.apply` сохраняет только target; source не изменяется. Если target active, runtime перепубликуется после успешной записи.

## Runtime reset и hot path

При connect/manual switch сбрасываются duplicate/own guards, lookup/pending queue, presence/HUD notices, periodic timers, compiled reply rules/filters/parsers и temporary overrides. Новый periodic schedule начинает полный interval; старые накопленные сообщения не отправляются. Snapshot deep immutable; JSON в incoming message/render не читается.

## Hardcode Vanilla-box

Vanilla-only strings локализованы в двух factories:

- `ServerCommandSettings.vanillaBoxDefaults()` — `/ignoreplayer`, `/clan lookup`, `/w`, `/pay`, `/call`, `/mail send`;
- `ParserSettings.vanillaBoxDefaults()` — Discord marker/name, channel separators, last seen/inactive/end/output/timestamp patterns.

Общий command/parser/filter/responder runtime не содержит fallback на эти defaults. Статический legacy parser helper остаётся только для characterization compatibility tests.

## Ограничения

- Root schema version сейчас 1.
- UI metadata editor использует comma-separated address patterns; command templates хранятся без начального `/`, кроме отдельного private reply prefix.
- Удаление после успешного root update удаляет отдельный template file; active/default protections предотвращают loss текущего selection.
- Minecraft client и реальные серверные formats требуют ручной проверки после изменения patterns/commands.
