# Конфигурация CNDL_chat+

Файл: `.minecraft/config/gasada-chat-responder.json`. Путь строит `ConfigManager` через `FabricLoader.getConfigDir()`. Имя является частью совместимости и не должно меняться.

При первом успешном чтении старого файла `LegacyConfigToVanillaBoxMigration` создаёт побайтовый backup `gasada-chat-responder.legacy-backup.json`, переносит server-specific поля в `server-templates/vanilla-box.json`, проверяет запись и последним сохраняет `server-templates.json`. Старый файл остаётся runtime-источником до этапа переключения и не удаляется. Повторный запуск не дублирует миграцию.

Ниже описана фактическая схема версии 0.4.3. Поля публичные и изменяемые; `schemaVersion` отсутствует. Отсутствующий config загружается через `ResponderConfig.defaults()`. Загруженный и сохраняемый объект проходит `ResponderConfig.sanitize()`.

## Поля `ResponderConfig`

| JSON-поле | Java-тип | Фактическое значение по умолчанию | Допустимость и текущие ограничения | Кто читает | Кто изменяет | Legacy/миграция |
|---|---|---|---|---|---|---|
| `enabled` | `boolean` | `true` | `true`/`false` | `ChatResponderEngine` | master toggle `ResponderScreen` | Нет |
| `discordChatEnabled` | `Boolean` | `true` | null при sanitize → `true` | Discord filter в `GasadaChatResponderClient` | toggle `ResponderScreen` | Nullable для старых JSON; восстанавливается |
| `discordMutedPlayers` | `List<String>` | `[]` | UI: Unicode letters/digits/`_`, 1..32; sanitize удаляет null/blank, trim, dedup ignore-case. При ручном JSON длина/символы не валидируются | Discord filter, blacklist UI | blacklist UI add/remove | null → `[]` |
| `mutedWords` | `List<String>` | `[]` | UI max 64 и непусто; sanitize удаляет null/blank, trim, dedup ignore-case. Ручной JSON не имеет max length | word filter, blacklist UI | blacklist UI add/remove | null → `[]` |
| `friends` | `List<String>` | `[]` | UI: `[A-Za-z0-9_]{1,16}`; sanitize только null/blank, trim, dedup ignore-case. Ручной JSON может содержать невалидный command argument | `ResponderScreen`, `FriendLookupManager`, `FriendsHud` | friends UI add/remove | null → `[]` |
| `friendLastSeen` | `Map<String,String>` | `{}` | sanitize удаляет записи с null/blank key/value; длина и формат server text не ограничены | friends UI | `FriendLookupManager`, remove friend | null → `{}` |
| `friendHudEnabled` | `Boolean` | `true` | null → `true` | `FriendsHud`, friends UI | HUD toggle с немедленным save | Nullable для старых JSON |
| `periodicMessages` | `List<PeriodicMessageConfig>` | один disabled slot с пустым текстом и интервалом 5 минут через `defaults()`/sanitize | null elements удаляются; пустой список получает один default slot; максимум 3; interval `<1` → 5. Верхний предел проверяется только UI | scheduler, periodic screen | periodic screen Save | null → list; принимает миграцию старых одиночных полей |
| `periodicEnabled` | `Boolean` | `null` | После sanitize всегда `null` | только `sanitize()` | `sanitize()` очищает | Старое поле одиночной рассылки; мигрируется, только если новый список пуст и поле не null |
| `periodicMessage` | `String` | `null` | null при миграции → `""`; после sanitize → null | только `sanitize()` | `sanitize()` очищает | Старое поле одиночной рассылки |
| `periodicIntervalMinutes` | `Integer` | `null` | null при миграции → 5; после sanitize → null | только `sanitize()` | `sanitize()` очищает | Старое поле одиночной рассылки |
| `rules` | `List<ReplyRule>` | при отсутствующем файле один rule: trigger `Всем привет`, response `привет`, channel `AUTO`, enabled; constructor default — `[]` | null elements удаляются; вложенные null strings → empty, null channel → `AUTO`; load не ограничивает длины | engine, rules UI | rules UI | null → `[]`; точная старая пара default rules заменяется одной новой |
| `globalPrefix` | `String` | `!` | UI max 16; null → `!`; пустое допустимо и блокирует global reply; control chars из JSON не фильтруются | channel detection/reply/own guard | channels UI | null восстанавливается |
| `clanReplyPrefix` | `String` | `/.` | UI max 32; null → `/.`; пустое допустимо и блокирует clan reply; control chars не фильтруются | channel candidates/reply/own guard | channels UI | null восстанавливается |
| `privateReplyCommand` | `String` | `/r` | UI max 64; null → `/r`; пустое блокирует private reply; содержимое не проверяется как безопасная команда | private reply | channels UI | null восстанавливается |
| `globalMarkers` | `String` | `(!),[g],[global],[глобальный],глобальный чат` | UI max 512; comma-separated; null → empty, затем обязательный отдельный marker `(!)` добавляется; иных ограничений нет | channel detection | channels UI | отсутствующий `(!)` восстанавливается |
| `clanMarkers` | `String` | `(клан),<клан>,〈клан〉,‹клан›` | UI max 512; comma-separated; null → empty; load не ограничивает длину/control chars | channel detection | channels UI | null → empty |
| `privateMarkers` | `String` | `[pm],[лс],личное сообщение,шепчет,->,→` | UI max 512; comma-separated; null → empty; load не ограничивает длину/control chars | channel detection | channels UI | null → empty |

Примечание: `addTextField` сначала задаёт общий max length 512; для трёх prefix/command fields после создания устанавливаются меньшие пределы 16/32/64.

## Вложенные объекты

### `ReplyRule`

| Поле | Тип | Default | Ограничения и использование |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Disabled rule пропускается engine |
| `trigger` | `String` | `""` | UI max 256; blank rule пропускается; config load не ограничивает длину/control chars; `*` — wildcard полного совпадения |
| `response` | `String` | `""` | UI max 256; blank rule пропускается; config load не ограничивает длину/control chars; может стать chat или command |
| `channel` | `ChatChannel` | `AUTO` | Только `AUTO`, `LOCAL`, `GLOBAL`, `CLAN`, `PRIVATE`; null → `AUTO`; неизвестное enum-значение делает Gson load ошибочным |

Rules проверяются в порядке списка; первое совпадение побеждает.

### `PeriodicMessageConfig`

| Поле | Тип | Default | Ограничения и использование |
|---|---|---|---|
| `enabled` | `boolean` | `false` | Disabled slot не планируется |
| `message` | `String` | `""` | UI max 256; null → empty; enabled+blank отклоняется UI; загруженный текст не очищается от control chars. Начальный `/` означает command |
| `intervalMinutes` | `int` | `5` | UI Save: 1..525600; sanitize: только `<1` → 5; scheduler умножает на `60_000L` |

## Sanitize и миграции

`ResponderConfig.sanitize()` выполняет одним методом:

1. Восстановление nullable fields и collections.
2. Удаление null/blank list entries.
3. Trim и case-insensitive dedup для Discord mute, muted words и friends.
4. Очистку неполных `friendLastSeen` entries.
5. Миграцию старой одиночной рассылки:
   - условие: `periodicMessages` пуст и `periodicEnabled != null`;
   - создаётся один `PeriodicMessageConfig`;
   - все три legacy fields затем устанавливаются в null.
6. Удаление null periodic slots, восстановление одного пустого slot и обрезку до первых трёх.
7. Восстановление null nested values и interval `<1`.
8. Восстановление prefix/markers и обязательного `(!)`.
9. Восстановление null rule fields.
10. Миграцию старых стандартных rules: ровно два правила с triggers `амадо где гасада`/`гасада где амадо` и response `тих тих` заменяются rule `Всем привет` → `привет`.

Миграции не версионированы. `ConfigManager.save()` также вызывает `sanitize()`, поэтому legacy fields исчезают при следующей успешной записи (Gson по умолчанию не сериализует null).

## Чтение и запись

### `load()`

- Если файла нет — возвращает `ResponderConfig.defaults()` без немедленного сохранения.
- Читает весь JSON как UTF-8 и десериализует Gson.
- JSON `null` → defaults.
- Любая ошибка логируется и заменяется defaults.
- Backup повреждённого файла не создаётся; состояние ошибки не возвращается вызывающему коду.

### `save()`

- Мутирует переданный объект через `sanitize()`.
- Пишет UTF-8 JSON в соседний `gasada-chat-responder.json.tmp`.
- Перемещает temp поверх основного файла с `ATOMIC_MOVE`; при `IOException` повторяет обычный replace move.
- Возвращает только boolean.
- Причина atomic fallback не логируется, cleanup temp после ошибки отсутствует, fsync отсутствует.

## Риски совместимости и потери данных

- После ошибки чтения приложение продолжает с defaults. Если пользователь откроет и закроет `ResponderScreen`, `removed()` вызовет save и может заменить повреждённый исходный файл defaults без backup.
- Многие callbacks игнорируют `false` от save; UI сообщает об успехе на основании выполненного действия, а не факта записи.
- UI часто меняет live config до save. При ошибке диска runtime продолжает работать с несохранёнными значениями.
- Ручной JSON обходит почти все UI length/character validations и может влиять на regex cost и server command construction.
- Старые одиночные periodic fields игнорируются, если новый список уже непуст; это фактический приоритет нового формата.
- Unknown `ChatChannel` или неверный JSON type приводит к полной ошибке load и defaults.

До любых изменений схемы нужны characterization tests для всех перечисленных ветвей и последовательные schema migrations.
