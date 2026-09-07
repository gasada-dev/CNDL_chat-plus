# CNDL_chat+ — задача для coding-агента: Chat Alerts + Закладки сообщений

Репозиторий: `https://github.com/gasada-dev/CNDL_chat-plus`

## Цель

Реализовать в CNDL_chat+ две законченные client-side функции:

1. **Chat Alerts** — пользовательские правила, которые отслеживают уже разрешённые к показу входящие сообщения и уведомляют пользователя через HUD и/или звук.
2. **Закладки сообщений** — сохранение конкретного сообщения из контекстного меню и отдельный экран просмотра/копирования/удаления сохранённых сообщений.

Нужна production-ready реализация с тестами, документацией и поддержкой обоих target'ов проекта. Не делай unrelated refactor и не меняй существующее поведение без необходимости.

---

# 0. Сначала изучи проект

Перед изменениями обязательно:

1. Прочитай корневой `AGENTS.md` целиком и соблюдай его как главный контракт проекта.
2. Прочитай только относящиеся к задаче документы:
   - `docs/ARCHITECTURE.md`
   - `docs/CONFIG.md`
   - `docs/FEATURE_MAP.md`
   - `docs/MANUAL_TESTS.md`
3. Найди production/test callers, readers и writers для:
   - `CndlChatPlusClient`
   - `ResponderConfig`
   - `ConfigManager`
   - `SettingsScreen`
   - `ChatVisibilityFilter`
   - `ChatTabClassifier`
   - `ChatTabController`
   - `ChatContextMenuController`
   - `ContextMenuBuilder`
   - `ChatMessageUnderMouseAccess`
   - `ChatMessageTarget`
   - `ChatMessageTextSanitizer`
   - `ChatMessageStore`
   - `ChatHistoryStore`
   - `ChatTimestamps`
   - `ChatDuplicateCollapser`
4. Проверь текущий worktree и не откатывай/не форматируй чужие изменения.
5. Перед рефакторингом существующего алгоритма сначала добавляй characterization test, если текущего покрытия недостаточно.

## Жёсткие ограничения

- Мод остаётся **client-only**.
- Поддержать **оба target'а проекта: Minecraft 1.21.11 и 26.2**.
- Не менять F8/F9 semantics.
- Не менять существующие `ChatChannel`/`ChatTab` значения только ради этой задачи.
- Не добавлять auto-reply/automation. Это принадлежит CNDL_toolkit.
- Не удалять и не изменять inert automation bridge в JSON.
- **Не создавать, не изменять и не удалять `UPDATE_NOTES.md`.**
- Не делать file I/O, HTTP или regex compilation в message/render hot path.
- UI, HUD, sound и Minecraft API — только client thread.
- Не сохранять config из `render()`.
- Не логировать содержимое чата, ЛС, sender payload и текст закладок.
- Не добавлять новые внешние зависимости без реальной необходимости. Для этой задачи они не нужны.
- Сохранять текущий принцип atomic write: sibling temp → move.
- При повреждённом новом JSON функция должна fail-open/fail-safe и не крашить клиент.
- Все новые UI-строки — на русском.
- Отступы в Java — tabs, один class = один файл, обычные classes остаются в flat package `ru.gasada.cndlchatplus`.

---

# 1. Chat Alerts

## 1.1. Пользовательское поведение

Пользователь должен иметь возможность создать несколько alert-правил.

Пример правила:

```text
Название: Моё имя
Включено: да
Тип совпадения: Текст
Шаблон: gasada
Канал: Любой
HUD: да
Звук: да
Cooldown: 5 сек
```

Когда новое **видимое** сообщение подходит правилу:

- при включённом HUD появляется короткое уведомление;
- при включённом звуке один раз проигрывается vanilla sound;
- одно входящее сообщение не должно проигрывать звук несколько раз, даже если одновременно совпало с несколькими правилами;
- cooldown применяется отдельно к каждому правилу;
- restored history при подключении **никогда не должна вызывать alert**.

### Важно

Alert работает только с сообщениями, которые уже прошли `ChatVisibilityFilter`.

То есть:

- muted word → alert не срабатывает;
- muted Minecraft player → alert не срабатывает;
- выключенный/muted Discord → alert не срабатывает;
- скрытый service lookup block → alert не срабатывает;
- overlay message → alert не срабатывает;
- сообщение, которое пользователь реально может увидеть → может вызвать alert.

Не пытайся использовать alerts как замену фильтру.

---

## 1.2. Scope первой версии

Нужно поддержать три типа совпадения:

```java
TEXT
WILDCARD
REGEX
```

И scope по каналу:

```java
ANY
GLOBAL
LOCAL
CLAN
PRIVATE
DISCORD
SYSTEM
```

Не добавляй в эту задачу:

- desktop/OS notifications;
- webhook;
- auto-reply;
- отправку команд;
- friend-only matcher;
- server-specific overrides;
- сложные expression trees (`AND/OR/NOT`);
- отдельные пользовательские звуковые файлы.

Это можно сделать позже.

---

## 1.3. Matching semantics

### TEXT

- case-insensitive substring match;
- использовать существующую нормализацию проекта, а не писать ещё один несовместимый lowercase helper;
- Unicode whitespace должен вести себя согласованно с текущими chat filters.

Пример:

```text
pattern = "gasada"
message = "Привет, Gasada!"
=> MATCH
```

### WILDCARD

Использовать текущую семантику `WildcardMatcher`: специальным символом является только `*`.

Пример:

```text
pattern = "*продам*алмазы*"
message = "срочно продам 32 алмазы сегодня"
=> MATCH
```

Не вводить новые wildcard-правила, несовместимые с существующим matcher проекта.

### REGEX

- regex должен валидироваться при save/edit;
- regex должен компилироваться заранее, не на каждом сообщении;
- matching case-insensitive + Unicode-aware, если это не ломает текущий стиль regex в проекте;
- invalid regex не должен попадать в active compiled rules;
- UI должен показать понятную ошибку и не сохранить некорректное правило.

### Текст для matching

Для проверки правила используй очищенный текст сообщения:

1. Убрать synthetic accessibility labels, которые уже удаляет `ChatMessageTextSanitizer` (например, от Chat Heads).
2. Не учитывать synthetic timestamp CNDL_chat+ как часть текста alert'а.
3. Не матчить по UI-only decorations, добавленным самим модом.

Если существующий sanitizer возвращает только строку для copy, расширь/переиспользуй его аккуратно, не ломая context menu tests.

---

## 1.4. Канал сообщения

Не делай собственный второй классификатор.

Используй существующую классификацию `ChatTabClassifier` / текущую source/channel infrastructure, чтобы alert и tabs одинаково понимали:

- Discord;
- private;
- clan;
- global;
- local;
- system.

`ANY` совпадает с любым из перечисленных каналов.

Если active template отсутствует, сохрани существующий fail-safe/fail-open подход и не подставляй скрытый `Vanilla-box` fallback.

---

## 1.5. Pipeline

Сохрани существующий смысл входящего pipeline.

Концептуально:

```text
MarriageLookupManager / FriendLookupManager interception
→ ChatVisibilityFilter
→ ChatAlertService             # новое, только accepted visible messages
→ ChatDuplicateCollapser / teleport handling
→ history/tabs
→ display
```

Не обязательно буквально вставлять один вызов между этими строками, если реальные Fabric callbacks устроены иначе. Главное — семантика:

- alert не видит скрытые сообщения;
- alert видит каждое реальное incoming event;
- duplicate collapse не обязан подавлять сам факт incoming alert;
- cooldown защищает от спама одинаковыми incoming events;
- history restore не вызывает alert.

### Duplicate behavior

Если один и тот же серверный текст пришёл 10 раз и UI схлопнул его в `x10`:

- `ChatAlertService` получает 10 incoming событий;
- rule cooldown решает, сколько уведомлений реально показать;
- один конкретный incoming event проигрывает максимум один alert sound;
- duplicate collapser продолжает работать как раньше.

---

## 1.6. Модель данных

Добавь глобальную конфигурацию alerts. Она не template-specific.

Предпочтительная структура:

```java
record / DTO ChatAlertRule(
    String id,
    String name,
    boolean enabled,
    ChatAlertMatchType matchType,
    String pattern,
    ChatAlertChannel channel,
    boolean hudEnabled,
    boolean soundEnabled,
    int cooldownSeconds
)
```

Конкретный mutable DTO/record выбери в соответствии с текущим config style проекта. Не ломай Gson serialization проекта.

В `ResponderConfig` добавить глобальные поля примерно такого смысла:

```json
{
  "chatAlertsEnabled": true,
  "chatAlertRules": []
}
```

### Defaults / sanitize

- `chatAlertsEnabled`: default `true`.
- отсутствующий `chatAlertRules`: пустой список.
- максимум правил: `100`.
- `id`: non-blank stable string; при создании генерировать UUID или другой стабильный уникальный ID.
- `name`: trim, max 64 chars; если blank — безопасное display name вроде `Alert`.
- `pattern`: trim, non-empty, max 256 chars.
- `cooldownSeconds`: clamp `0..3600`.
- null enum → безопасный default (`TEXT`, `ANY`).
- null/invalid nested entries не должны крашить load.
- exact handling automation bridge не менять.

Если config schema проекта требует отдельной migration для новых nullable/global fields — сделай минимальную backward-compatible migration. Если текущий Gson + sanitize позволяет добавить поля без schema bump, не bump'ай schema просто так.

`ConfigManager.saveGlobalSettings` должен сохранять новые global alert fields, не перезаписывая server-specific fields active template и inert automation bridge.

---

## 1.7. Compiled rules

Нужен отдельный runtime слой, например:

```text
ChatAlertRule
→ ChatAlertRuleCompiler
→ CompiledChatAlertRule
→ ChatAlertService
```

Точное имя выбирай по стилю проекта.

Требования:

- regex/wildcard preparation не выполняется для каждого сообщения;
- compiled snapshot меняется только после load/save UI/config;
- опубликованный runtime список immutable;
- invalid rule пропускается с безопасным warning без pattern/message payload;
- message handler только выполняет уже подготовленный matcher.

Так как alerts глобальные, не засовывай их искусственно в `ActiveTemplateSnapshot`. Они не должны переключаться вместе с server template.

При disconnect очистить transient cooldown/HUD state, но не пользовательские правила.

---

## 1.8. Cooldown

Cooldown хранится **runtime-only** по `rule.id`.

Требования:

- `0` = без cooldown;
- время сравнивать монотонно или через инъецируемый clock, удобный для unit tests;
- не сохранять last-trigger time в JSON;
- disconnect очищает cooldown state;
- редактирование/удаление правила не оставляет вечный stale state;
- изменение pattern с тем же ID может оставить cooldown только если это явно проще и тест покрывает поведение; предпочтительно сбросить state после save правил.

---

## 1.9. HUD

Добавь лёгкий transient HUD notice, не screen/modal.

Пример текста:

```text
Alert «Моё имя»: Steve: привет gasada
```

Требования:

- показывать примерно 4 секунды;
- длинный текст безопасно обрезать для HUD;
- не хранить сообщение после timeout;
- render только рисует immutable/transient snapshot;
- render не матчится, не компилирует regex, не сохраняет config и не проигрывает звук;
- новый alert может заменить предыдущий или использовать маленькую очередь максимум 3 notices. Выбери более простой вариант; предпочтительно очередь до 3.

Если несколько правил совпали с одним incoming message, показать **одно** HUD уведомление. В тексте можно использовать имя первого сработавшего правила согласно порядку rules.

---

## 1.10. Sound

- Использовать vanilla sound event, доступный в обоих target'ах.
- Не добавлять audio resource.
- Sound запускается на client thread и не из render.
- Если одно сообщение совпало с тремя правилами с `soundEnabled=true`, звук всё равно проигрывается **один раз**.
- Если все совпавшие rules находятся в cooldown — звука/HUD нет.

По возможности переиспользуй существующий pattern friend/TP sound scheduling, вместо создания третьего несовместимого механизма.

---

## 1.11. UI Chat Alerts

В `SettingsScreen` добавить кнопку:

```text
Chat Alerts
```

Она открывает новый экран, например `ChatAlertsScreen`.

### Экран списка

Минимально:

```text
Chat Alerts                         [Вкл/Выкл]

[✓] Моё имя        Текст: gasada          Любой       HUD + звук
[✓] Trade          Маска: *продам*        Глобал      HUD
[ ] Debug          Regex: ...             Система     звук

[Добавить] [Изменить] [Удалить]
[Готово]
```

Можно использовать row buttons вместо точной таблицы — главное, чтобы экран нормально работал на стандартных размерах GUI.

Требования:

- глобальный toggle `chatAlertsEnabled`;
- выбрать rule;
- add/edit/delete;
- enabled toggle для rule;
- сохранить порядок rules;
- не выполнять save в render;
- при закрытии/`Готово` использовать текущий save pattern проекта;
- после успешного save немедленно перестроить compiled alerts runtime.

### Экран редактирования

Поля:

- `Название`
- `Тип`: `Текст / Маска / Regex`
- `Шаблон`
- `Канал`: `Любой / Глобал / Локал / Клан / ЛС / Discord / Система`
- `HUD`: вкл/выкл
- `Звук`: вкл/выкл
- `Cooldown, сек`: 0..3600
- `Правило включено`: вкл/выкл

Кнопки:

- `Сохранить`
- `Отмена`

На invalid regex / пустой pattern `Сохранить` не должен молча закрывать экран. Показать status/error в стиле текущего UI.

---

# 2. Закладки сообщений

## 2.1. Пользовательское поведение

В существующее контекстное меню сообщения добавить действие:

```text
Сохранить в закладки
```

Действие доступно для:

- Minecraft player messages;
- Discord messages;
- system/broadcast messages;
- private/clan/global/local messages.

Для bookmarks **не требуется sender**. Любое сообщение, для которого context menu уже умеет получить корректный target, можно сохранить.

После сохранения сообщение появляется в отдельном экране **«Закладки сообщений»**.

На этом экране пользователь может:

- посмотреть сохранённый текст;
- увидеть время и канал;
- увидеть sender, если он корректно распознан;
- скопировать текст;
- удалить одну закладку;
- очистить все закладки текущего сервера через подтверждение.

В первой версии **не делать jump-to-original-message**. Это отдельная задача, потому что стабильная привязка к vanilla `GuiMessage` после reconnect требует дополнительной identity/navigation модели.

---

## 2.2. Независимость от истории

Критически важно:

> Закладки не должны зависеть от `chatHistoryEnabled` или `chatHistoryPersist`.

Пользователь мог отключить обычную историю, но вручную сохранить важное сообщение. Такая закладка всё равно должна пережить reconnect.

Не хранить bookmarks внутри ring buffer `ChatMessageStore`.

Не удалять bookmarks при очистке/лимите обычной chat history.

---

## 2.3. Persistence

Сделать отдельное per-server хранилище рядом с history, например:

```text
.minecraft/config/cndl-chat-plus-chat-bookmarks/<server>.json
```

Используй тот же безопасный `fileKey`/server address normalization подход, что и `ChatHistoryStore`, а не изобретай второй несовместимый sanitizer пути.

Предпочтительные классы:

```text
ChatBookmark
ChatBookmarkStore
ChatBookmarkPersistence / ChatBookmarkRepository
```

Названия можно скорректировать под стиль проекта.

### Atomic save

- sibling `.tmp`;
- flush/write;
- atomic move где поддерживается;
- явная обработка I/O errors;
- никакого save из render/message hot path.

Добавление/удаление закладки — редкое явное UI action, поэтому допустимо persist сразу после mutation по текущему безопасному repository pattern проекта.

На disconnect также можно сделать final save dirty state, если это вписывается в lifecycle.

### Corrupt JSON

При повреждённом bookmark JSON:

- клиент не падает;
- warning без содержимого сообщений;
- текущий runtime bookmark list пустой;
- остальные функции чата продолжают работать.

---

## 2.4. Что хранить

Не нужно сериализовать полный Minecraft `Component`, если это делает feature хрупкой между target'ами.

Для первой версии достаточно стабильного текстового bookmark DTO:

```java
record ChatBookmark(
    String id,
    long savedAtMillis,
    Long messageTimestampMillis,
    String channel,
    String sender,
    String text
)
```

Плюс server scope определяется самим per-server файлом.

Точные nullable semantics выбери в соответствии со стилем Gson проекта.

### Поля

- `id`: уникальный stable ID.
- `savedAtMillis`: обязательное время сохранения.
- `messageTimestampMillis`: исходный timestamp сообщения, если существующая infrastructure позволяет получить его надёжно; иначе `null`.
- `channel`: стабильное имя существующего `ChatTab`/channel, без введения нового enum только ради JSON, если это не нужно.
- `sender`: nullable/blank для system; использовать существующий `ChatMessageSenderExtractor`, не писать новый parser.
- `text`: очищенный plain text сообщения.

### Canonical bookmark text

Перед сохранением:

- убрать synthetic Chat Heads labels через `ChatMessageTextSanitizer`;
- не сохранять synthetic CNDL timestamp `[HH:mm]` как часть `text`, если timestamp уже доступен как metadata;
- не сохранять UI-only alert decoration;
- сохранить реальное видимое содержимое сообщения;
- допустимо сохранить `xN` duplicate suffix, если это реальная текущая collapsed строка и отделить его без хрупкого парсинга нельзя.

Не логировать `text` или `sender`.

### Limits / sanitize

Добавь защиту от разросшегося/повреждённого файла:

- максимум `5000` bookmarks на один server file;
- `text` max `8192` chars;
- `sender` max `64` chars;
- `channel` unknown → `SYSTEM` или display `Неизвестно`, но без crash;
- invalid/null bookmark entry пропустить;
- duplicate IDs исправить/пропустить детерминированно;
- сортировка в UI: новые сверху по `savedAtMillis`.

Не надо автоматически deduplicate одинаковые сообщения: пользователь может намеренно сохранить два одинаковых сообщения в разное время.

---

## 2.5. Multiplayer / singleplayer

Для multiplayer используй тот же server identity/fileKey подход, что и history.

Если у singleplayer нет уже существующего стабильного history key:

- не придумывай в этой задаче новую world identity schema;
- разрешается сделать singleplayer bookmarks session-only;
- в UI/коде это должно fail-safe работать без NPE;
- документируй ограничение в `docs/CONFIG.md` / `docs/MANUAL_TESTS.md`.

Если в текущем main уже существует подходящий стабильный world key — переиспользуй его.

---

## 2.6. Context menu

Расширить существующие `ContextMenuBuilder` / `ChatContextMenuController`, а не создавать параллельное второе меню.

Предпочтительный порядок начала меню:

```text
Копировать ник          # если sender есть
Копировать сообщение
Сохранить в закладки
--------------------
... player/server actions
```

Для Discord/system:

```text
Копировать Discord-имя  # если применимо
Копировать сообщение
Сохранить в закладки
```

Требования:

- bookmark action не зависит от наличия active server command template;
- без sender bookmark всё равно доступна;
- клик сохраняет именно target под курсором;
- wrapped message сохраняется один раз как целое parent message;
- chat scale / line spacing / scroll / active tab / active search не должны ломать target;
- при `chatContextMenuEnabled=false` не добавлять отдельный обходной ПКМ handler: vanilla behavior остаётся как сейчас.

После сохранения можно показать короткий status/toast/HUD:

```text
Сообщение сохранено в закладки
```

Не надо проигрывать звук.

В первой версии context menu не обязан определять, что конкретная строка уже сохранена. То есть пункт всегда может называться `Сохранить в закладки`, а удаление делается на экране bookmarks. Это специально упрощает identity model и избегает ложного toggle для одинаковых сообщений.

---

## 2.7. Экран «Закладки сообщений»

Добавить кнопку из F8 UI. Предпочтительно разместить её в `ResponderScreen`/общем header рядом с глобальными служебными экранами, либо в `SettingsScreen`, если layout главного экрана уже плотный.

Выбери вариант с меньшим риском переполнения layout. Не меняй размеры/поведение существующих двух основных вкладок без необходимости.

Новый `ChatBookmarksScreen` должен показывать bookmarks **текущего server scope**.

Пример строки:

```text
[04.09.2026 01:15] [ЛС] Steve
координаты базы: 123 64 -456
                         [Копировать] [Удалить]
```

Для system:

```text
[04.09.2026 01:18] [Система]
Сервер будет перезапущен через 5 минут
                         [Копировать] [Удалить]
```

### Минимальные элементы

- title `Закладки сообщений`;
- label текущего сервера/template scope, если доступен;
- список;
- pagination через существующий `Pagination`, если подходит;
- `Копировать`;
- `Удалить`;
- `Очистить все`;
- `Готово`/`Назад`.

### Clear all

`Очистить все` обязательно требует подтверждение отдельным screen/dialog в стиле Minecraft/UI проекта.

Нельзя удалить все bookmarks одним случайным кликом.

### Copy

Копируется только `text`, без автоматического добавления sender/channel/timestamp.

Clipboard action локальная.

---

# 3. Composition root / lifecycle

`CndlChatPlusClient` остаётся composition root.

Создай services/stores один раз при bootstrap и передай зависимости явно, вместо global static доступа, если текущая архитектура это позволяет.

Ожидаемый lifecycle:

## На client init

- load/sanitize global alert config;
- compile alert rules;
- register UI/events/HUD.

## На server join

- resolve active template как раньше;
- reset transient alert cooldown/HUD state;
- load bookmarks текущего server key;
- **не** запускать alerts на восстановленной chat history.

## На message

- lookup interception;
- visibility;
- alerts;
- duplicate/TP/history/tabs согласно текущей архитектуре.

## На disconnect

- clear alert runtime state;
- save/clear active bookmark store корректно;
- не переносить bookmarks одного сервера в другой.

## На template switch

Alerts глобальные и rules не переключаются.

Но channel classification для новых сообщений должна использовать новый active template, как и tabs.

Bookmarks принадлежат server connection, а не template ID. Простое переключение template на том же сервере не должно потерять bookmark list.

---

# 4. Настройки и обратная совместимость

Новые alert fields должны быть backward compatible с существующим `cndl-chat-plus.json`.

Обязательно проверить:

- старый config без alerts загружается;
- default alerts enabled, rules пустой;
- save global settings не меняет server-specific friends/mutes/markers;
- save не меняет inert responder/rules/periodic fields;
- switching templates не меняет global alert rules;
- corrupt alert rule не ломает весь config;
- duplicate IDs / null entries sanitizes безопасно.

Bookmarks — отдельные данные и не должны попадать в template import/export.

Не добавляй bookmarks в `ServerTemplate`, `ActiveTemplateSnapshot` или template catalog.

---

# 5. Тесты

Не ограничивайся ручной проверкой. Добавь JUnit 5 tests рядом с существующими.

## 5.1. Chat Alerts unit tests

Минимально нужны тесты для:

### Matching

- TEXT case-insensitive contains;
- TEXT no match;
- WILDCARD через существующую semantics;
- REGEX match;
- invalid REGEX rejected/omitted at compile;
- synthetic Chat Heads label не влияет на match;
- timestamp decoration не влияет на match, если применимо на этом уровне.

### Channel

Отдельно проверить:

- ANY;
- GLOBAL;
- LOCAL;
- CLAN;
- PRIVATE;
- DISCORD;
- SYSTEM;
- channel mismatch не trigger'ит rule.

Не дублируй тесты `ChatTabClassifier`; здесь достаточно доказать интеграцию alert scope с его результатом.

### Cooldown

- first trigger fires;
- second inside cooldown suppressed;
- after cooldown fires;
- cooldown `0` fires every time;
- cooldown независим для разных rule IDs;
- reset очищает cooldown.

Используй fake/injected clock, не `Thread.sleep()`.

### Multiple rules

Одно incoming message подходит двум rules:

- matched rules корректно определены;
- sound action агрегируется в один;
- HUD action агрегируется в один;
- rules в cooldown не дают action.

### Global disable

`chatAlertsEnabled=false` полностью выключает actions без изменения остальных chat features.

---

## 5.2. Alert config tests

Добавить/расширить tests для:

- old JSON without fields;
- null lists;
- null nested rule;
- blank pattern;
- too-long pattern/name;
- invalid enum;
- cooldown clamp;
- 100-rule limit;
- exact round-trip пользовательских rules;
- global save не повреждает automation bridge;
- template switch не меняет rules.

---

## 5.3. Bookmarks tests

### Store

- add;
- remove by ID;
- clear;
- insertion of identical text twice creates two independent bookmarks;
- order newest first;
- max 5000 bound.

### Persistence

- save/load round-trip;
- per-server isolation;
- valid server fileKey;
- atomic temp file cleanup;
- corrupt JSON fail-safe;
- null/invalid entry skip;
- oversized text truncation/rejection согласно выбранной policy;
- duplicate IDs handled deterministically.

### Context menu

Обновить tests `ContextMenuBuilder` / related tests:

- normal Minecraft sender: bookmark action присутствует;
- Discord sender: присутствует;
- system without sender: присутствует;
- active template missing: bookmark action всё равно присутствует;
- command actions при этом по-прежнему исчезают как раньше;
- action order соответствует спецификации;
- synthetic Chat Heads label не попадает в saved text.

### Independence from history

Доказать тестом на service/store уровне:

- bookmarks можно add/save/load при `chatHistoryEnabled=false`;
- chat history limit/clear не трогает bookmark store.

---

# 6. Manual tests

Допиши `docs/MANUAL_TESTS.md` отдельными подразделами.

## 6.1. Chat Alerts

Проверить минимум:

1. Создать TEXT rule `gasada`, ANY, HUD+sound, cooldown 5.
2. Получить обычное сообщение с `Gasada` в другом регистре → один HUD + один sound.
3. Сразу получить ещё одно → cooldown suppress.
4. Через >5 сек получить снова → alert.
5. Создать PRIVATE-only rule → global не trigger, ЛС trigger.
6. Создать wildcard rule.
7. Создать regex rule.
8. Ввести invalid regex → UI не сохраняет.
9. Сообщение от muted player → alert отсутствует.
10. Сообщение с muted word → alert отсутствует.
11. Выключить Discord → Discord alert отсутствует.
12. Получить несколько identical сообщений при duplicate collapse → строка схлопывается как раньше, alerts ограничиваются cooldown.
13. Переподключиться с persisted history → старые сообщения появляются без alert sound/HUD.
14. Отключить global `Chat Alerts` → rules остаются в config, но не trigger'ятся.
15. Переключить server template → rules сохраняются, channel classification использует новый template.
16. Проверить оба Minecraft target'а.

## 6.2. Bookmarks

1. ПКМ по player message → `Сохранить в закладки`.
2. Открыть экран bookmarks → message присутствует.
3. Copy → clipboard содержит только очищенный message text.
4. Удалить → bookmark исчезает и после reopen отсутствует.
5. Сохранить два одинаковых сообщения → две записи.
6. Сохранить Discord message.
7. Сохранить system message без sender.
8. Сохранить wrapped message → одна bookmark целого сообщения.
9. Проверить при non-default chat scale / line spacing / scroll / active tab / active search.
10. Отключить history и history persist → bookmark всё равно сохраняется между reconnect.
11. Перезайти на тот же server → bookmarks восстановлены.
12. Зайти на другой server → первый server bookmarks не смешиваются.
13. Повредить bookmarks JSON → клиент не падает.
14. `Очистить все` → сначала confirmation, затем пустой список.
15. Проверить Chat Heads: synthetic `[... head]` не сохраняется.
16. Проверить оба target'а.

---

# 7. Документация

После реализации обновить:

- `docs/ARCHITECTURE.md`
  - alerts pipeline;
  - runtime compiled rules/cooldown/HUD;
  - bookmarks persistence/lifecycle.
- `docs/CONFIG.md`
  - `chatAlertsEnabled`;
  - `chatAlertRules` schema/defaults/sanitize;
  - bookmarks directory/limits;
  - singleplayer limitation, если она остаётся.
- `docs/FEATURE_MAP.md`
  - отдельная строка Chat Alerts owner → data → tests;
  - отдельная строка Bookmarks owner → data → tests.
- `docs/MANUAL_TESTS.md`
  - сценарии выше.
- `README.md`
  - коротко упомянуть Chat Alerts и bookmarks в списке возможностей, без переписывания всего README.

**Не трогать `UPDATE_NOTES.md`.**

`CHANGELOG.md` и version bump не делать, если это не было отдельно запрошено пользователем или текущий task runner явно требует release preparation.

---

# 8. Предпочтительная декомпозиция классов

Это ориентир, а не приказ слепо создать все названия. Сначала сравни с текущим кодом.

## Alerts

```text
ChatAlertMatchType
ChatAlertChannel
ChatAlertRule
CompiledChatAlertRule
ChatAlertRuleCompiler
ChatAlertService
ChatAlertDecision
ChatAlertHud / ChatAlertHudState
ChatAlertsScreen
ChatAlertEditScreen
```

Если часть ответственности естественно помещается в существующие services, не плодить классы ради количества.

## Bookmarks

```text
ChatBookmark
ChatBookmarkStore
ChatBookmarkPersistence (или ChatBookmarkRepository)
ChatBookmarksScreen
```

Переиспользовать:

```text
ChatMessageTextSanitizer
ChatMessageSenderExtractor
ChatTabClassifier
Pagination
ScreenStatus
UiConstants
server fileKey/history path helpers
```

Если helper сейчас private внутри `ChatHistoryStore`, аккуратно вынеси reusable package-private helper с characterization tests вместо copy-paste.

---

# 9. Definition of Done

Задача считается завершённой, когда выполнено всё ниже.

## Chat Alerts

- [ ] Есть глобальный toggle.
- [ ] Есть UI списка rules.
- [ ] Есть add/edit/delete/enable rule.
- [ ] TEXT работает case-insensitive.
- [ ] WILDCARD использует текущую semantics проекта.
- [ ] REGEX валидируется и компилируется вне hot path.
- [ ] Есть channel scope ANY/GLOBAL/LOCAL/CLAN/PRIVATE/DISCORD/SYSTEM.
- [ ] Есть per-rule HUD toggle.
- [ ] Есть per-rule sound toggle.
- [ ] Есть per-rule cooldown 0..3600.
- [ ] Hidden messages не trigger alerts.
- [ ] Restored history не trigger alerts.
- [ ] Multiple matched rules не дублируют sound на одном message event.
- [ ] Disconnect очищает transient state.
- [ ] Template switch не теряет global rules.

## Bookmarks

- [ ] Context menu умеет сохранить любое target message.
- [ ] Sender не обязателен.
- [ ] Bookmarks имеют отдельное per-server persistence.
- [ ] Bookmarks не зависят от chat history toggles.
- [ ] Есть экран списка текущего сервера.
- [ ] Есть copy.
- [ ] Есть delete.
- [ ] Есть clear all с confirmation.
- [ ] Identical messages можно сохранить несколько раз.
- [ ] Chat Heads synthetic label не сохраняется.
- [ ] Corrupt bookmark JSON не крашит клиент.
- [ ] Данные серверов не смешиваются.

## Quality

- [ ] `docs/FEATURE_MAP.md` обновлён.
- [ ] `docs/ARCHITECTURE.md` обновлён.
- [ ] `docs/CONFIG.md` обновлён.
- [ ] `docs/MANUAL_TESTS.md` обновлён.
- [ ] `README.md` кратко обновлён.
- [ ] `UPDATE_NOTES.md` не изменён.
- [ ] Нет unrelated formatting/refactor.
- [ ] Нет message content/private data в logs.
- [ ] Нет I/O/regex compilation в render/message hot path.
- [ ] Оба target'а компилируются.

---

# 10. Обязательная финальная проверка агента

Запусти:

```bash
./gradlew test
./gradlew build
git diff --check
```

Если проект имеет отдельные documented команды проверки target'ов — выполни и их согласно `docs/RELEASE.md`, но не начинай release/push без отдельной просьбы.

Потом проверь:

```bash
git status --short
git diff --stat
git diff -- UPDATE_NOTES.md
```

Последняя команда должна показать отсутствие изменений `UPDATE_NOTES.md`.

---

# 11. Что вернуть пользователю после реализации

В финальном ответе coding-агента перечислить:

1. Что реализовано по Chat Alerts.
2. Что реализовано по bookmarks.
3. Какие production files добавлены/изменены.
4. Какие tests добавлены/изменены.
5. Какие docs обновлены.
6. Результаты `./gradlew test`, `./gradlew build`, `git diff --check`.
7. Какие manual scenarios всё ещё требуют запуска внутри Minecraft.
8. Известные ограничения/риски.
9. Явно подтвердить, что `UPDATE_NOTES.md` не изменялся.

Не заявлять, что Minecraft UI проверен вручную, если агент реально не запускал клиент и не выполнял сценарии.

---

# 12. Приоритет реализации

Делай небольшими логическими шагами в таком порядке:

1. Alert DTO/config sanitize + tests.
2. Compiled alert matcher/service + cooldown tests.
3. Alert integration в incoming pipeline.
4. Alert HUD/sound.
5. Alert UI.
6. Bookmark DTO/store/persistence + tests.
7. Context-menu bookmark action.
8. Bookmarks screen.
9. Lifecycle/server isolation.
10. Documentation + manual test plan.
11. Полный test/build/diff-check.

Если обнаружится архитектурный конфликт с текущим main, не обходи его грязным static hack. Сохрани существующие contracts и выбери минимальное решение, которое укладывается в текущую архитектуру.
