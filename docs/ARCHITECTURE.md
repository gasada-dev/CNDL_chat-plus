# Архитектура CNDL_chat+

Документ фиксирует состояние исходников версии 0.4.3 до рефакторинга. Все классы находятся в пакете `ru.gasada.chatresponder` и в client source set.

## Запуск и зависимости

```text
Fabric client entrypoint
→ GasadaChatResponderClient.onInitializeClient()
→ ConfigManager.load()
→ ChatResponderEngine(config)
→ PeriodicMessageScheduler(config, engine)
→ UpdateChecker()
→ FriendLookupManager(config)
→ FriendsHud.register(config)
→ регистрация F8, END_CLIENT_TICK и событий входящих сообщений
```

`GasadaChatResponderClient` хранит загруженный объект в публичном статическом поле `CONFIG`, а менеджер lookup — в `FRIEND_LOOKUP`. Один и тот же изменяемый экземпляр `ResponderConfig` передаётся engine, scheduler, lookup, HUD и экранам.

Дополнительно bootstrap создаёт `ServerTemplateRuntime`. Его `ActiveTemplateSnapshot` является глубокой immutable-копией данных шаблона и имеет monotonically increasing generation. `TemplateSwitchCoordinator` при программном переключении очищает duplicate/own guards, lookup queue, HUD presence/notices, periodic timers и compiled wildcard filters. `TemplateSelectionService` загружает default до обработки сообщений и при новом connection разрешает шаблон по фактическому `ServerData.ip`; неуспех очищает runtime вместо применения чужих настроек. JSON в горячем пути не читается.

## Входящее сообщение

Фактический поток регистрации:

```text
ClientReceiveMessageEvents.ALLOW_CHAT
→ FriendLookupManager.shouldShowSystemMessage(message, false)
→ GasadaChatResponderClient.shouldShowDiscordMessage(message)
→ при разрешении: ClientReceiveMessageEvents.CHAT
→ ChatResponderEngine.handlePlayerMessage(...)

ClientReceiveMessageEvents.ALLOW_GAME
→ overlay всегда разрешён
→ иначе FriendLookupManager.shouldShowSystemMessage(message, false)
→ GasadaChatResponderClient.shouldShowDiscordMessage(message)
→ при разрешении: ClientReceiveMessageEvents.GAME
→ ChatResponderEngine.handleSystemMessage(message, overlay)
→ overlay повторно отбрасывается внутри engine
```

В выражениях `ALLOW_CHAT`/`ALLOW_GAME` используется short-circuit: lookup вызывается раньше `ChatVisibilityFilter`. Поэтому lookup может скрыть сообщение или изменить своё состояние, после чего остальные фильтры для этого сообщения не выполняются. Фильтр затем проверяет Discord toggle/mute и muted words только из immutable active snapshot. `CompiledFilterSet` строится при switch шаблона. Скрытое событием `ALLOW_*` сообщение не должно дойти до обычного `CHAT`/`GAME` callback и запустить автоответ; это необходимо сохранить и отдельно проверить вручную при обновлении Fabric API.

### ALLOW_CHAT и ALLOW_GAME

- `ALLOW_CHAT` применяет lookup-фильтр, общий Discord toggle, Discord-муты и muted words к player chat.
- `ALLOW_GAME` применяет тот же путь к не-overlay game/system messages.
- Для `overlay == true` `ALLOW_GAME` возвращает `true` без lookup и фильтрации.
- `FriendLookupManager.shouldShowSystemMessage` сейчас вызывается и для chat, и для game messages. Он безусловно скрывает пустые строки, отдельные timestamps и любой текст, совпавший с широкими lookup patterns, даже когда lookup не ожидается.

### CHAT и GAME

- `CHAT` передаёт displayed component, signed content и sender profile в `ChatResponderEngine.handlePlayerMessage`; сообщение локального UUID игнорируется.
- `GAME` передаёт component и overlay в `handleSystemMessage`; overlay engine не обрабатывает.
- Engine дополнительно отбрасывает вероятные собственные сообщения, дубль в окне 400 мс и недавно отправленный текст в окне 5 секунд.
- Затем engine определяет канал, строит кандидаты, проходит правила по порядку и отправляет первый ответивший rule.

### Определение канала и кандидаты

`ChatChannelDetector` использует `ActiveTemplateSnapshot` и заранее созданный `CompiledParserSettings`, проверяя признаки строго в таком порядке:

1. Discord marker → `GLOBAL`.
2. Любой `privateMarkers` → `PRIVATE`.
3. Любой `clanMarkers` → `CLAN`.
4. signed content начинается с `globalPrefix` → `GLOBAL`.
5. displayed содержит обязательный `(!)` или `globalMarkers` → `GLOBAL`.
6. Иначе → `LOCAL`.

`ChatResponderEngine` координирует вызовы, а состояние и алгоритмы разделены: `OwnMessageGuard`, `DuplicateMessageGuard`, `ReplyCandidateBuilder` и compiled-on-switch `ReplyRuleMatcher`. Оба временных guard используют инъецируемый `LongSupplier`; matcher читает только rule snapshots активного шаблона и сохраняет правило «первое подходящее включённое правило побеждает».

Кандидаты: исходный content, displayed text, текст без global/clan prefix и части после последнего separator активного шаблона. Они нормализуются через lowercase `Locale.ROOT`, trim и схлопывание whitespace. `WildcardMatcher` сопоставляет маску rule с кандидатом целиком в режиме `FULL_MATCH`; muted words используют `CONTAINS_MATCH`. Только `*` означает любую последовательность, остальные regex-символы экранируются. Скомпилированные rules и filters заменяются вместе с active snapshot.

### Формирование ответа

- `LOCAL`: response без добавленного префикса.
- `GLOBAL`: добавляется `globalPrefix`, если response с него не начинается.
- `CLAN`: добавляется `clanReplyPrefix` и пробел.
- `PRIVATE`: если response не начинается с `/`, добавляется `privateReplyCommand` и пробел.
- Итог, начинающийся с `/`, уходит через `sendCommand` без первого `/`; остальное — через `sendChat`.

Отправка reply помещается в `minecraft.execute`. Периодические и UI-команды отправляются напрямую из client tick или callback экранов.

## Client tick

### Глобальный `END_CLIENT_TICK`

`GasadaChatResponderClient` на каждом конце client tick:

1. Потребляет все нажатия F8 и открывает новый `ResponderScreen(CONFIG)`.
2. Вызывает `PeriodicMessageScheduler.tick(minecraft)`.
3. Вызывает `FRIEND_LOOKUP.tick(minecraft)`.
4. Вызывает `UpdateChecker.tick(minecraft)`.

`PeriodicMessageScheduler.tick` проходит ровно `PeriodicMessageConfig.MAX_PERIODIC_MESSAGES` (3) состояний active snapshot, сбрасывает неактивные/невалидные/отключённые слоты, сравнивает текст+интервал с сохранённым fingerprint, планирует первый запуск после полного интервала и отправляет просроченные сообщения через `OutgoingChatService`. Template switch очищает все timers до публикации нового snapshot.

`FriendLookupManager.tick` очищает очередь при disconnect, принимает только друзей active snapshot, обрабатывает timeout 7 секунд, выдерживает 2,5 секунды между командами и запрашивает следующий lookup через `FriendActionService`/`ServerCommandService`. Ответ разбирает `FriendLookupParser` с compiled patterns активного шаблона; `last seen` публикуется обратно в тот же template scope.

`FriendPresenceTracker` обновляется из client tick и сохраняет прежние интервалы: warmup 30 секунд, подтверждение offline 5 секунд и notice 4 секунды. Он публикует immutable `FriendHudSnapshot`; `FriendsHud` в render только рисует snapshot. Звук запускается в tick, а reconnect/template switch очищает tracker до обработки нового списка.

`UpdateChecker.tick` запускает единственную асинхронную проверку после входа на сервер и при отсутствии GUI; затем на tick открывает `UpdateAvailableScreen`, когда проверенный результат готов и экран свободен.

### Tick `ResponderScreen`

Когда открыта вкладка друзей, `ResponderScreen.tick()` раз в 20 screen ticks заново вычисляет множество online-друзей и hash `friendLastSeen`. При изменении полностью перестраивает widgets через `rebuildContents()`.

## Render

### HUD render

`FriendsHud.render` сейчас не ограничен рисованием. В каждом HUD render callback он:

- определяет disconnect/смену connection и очищает статическое состояние;
- получает системное время;
- проходит друзей и строит online list/set;
- управляет 30-секундным warmup, 5-секундным подтверждением offline и 4-секундными notices;
- обновляет `previousOnline`, `offlineSince`, `onlineNotices`;
- запускает `PLAYER_LEVELUP` sound при появлении друга;
- измеряет строки, рисует панель online-друзей и notices.

Это главный render hot spot и смешение presentation/state transitions. Файлы и команды из HUD не используются.

### Screen render

- `ResponderScreen.extractBackground` и `extractRenderState` рисуют фон, панель, заголовки вкладок, статусы и подпись; для друзей читают выбранного друга, online snapshot и `friendLastSeen`.
- `PeriodicMessageScreen.extractBackground`/`extractRenderState` рисуют панель, help/status и подпись.
- `UpdateAvailableScreen.extractBackground`/`extractRenderState` рисуют update panel и переносимый текст manifest.
- `CreditRenderer.draw` рисует подпись `create by CNDL` посимвольно с интерполяцией цвета.

## Периодические сообщения

`PeriodicMessageScreen` создаёт копии `PeriodicMessageConfig` в `drafts`. При Save он парсит интервалы, требует 1..525600 и непустой текст для enabled slots, затем заменяет `config.periodicMessages` до вызова `ConfigManager.save`. Scheduler видит изменения через общий объект config. Значение, начинающееся с `/`, отправляется как команда; любое другое — как chat. Три `State` созданы жёстко в конструкторе.

## Friend lookup и друзья

- Вкладка друзей ставит весь `config.friends` в очередь один раз за жизнь экземпляра экрана; новый друг ставится отдельно.
- Queue принимает любое непустое имя и устраняет дубли без учёта регистра, но не проверяет Minecraft nickname format.
- Lookup manager распознаёт `Был в сети`, `Был онлайн`, `Неактивен`, конец на `Тип убийства` и широкий набор служебных строк.
- При завершении или timeout найденный last-seen записывается в `config.friendLastSeen`, затем вызывается `ConfigManager.save`.
- Вкладка друзей самостоятельно вычисляет online state, формирует и отправляет `/w`, `/pay`, `/call`, `/mail send`, управляет выбором, lookup queue, config mutations и подсказками.
- `FriendsHud` независимо повторно вычисляет online state для HUD и уведомлений.

## Проверка обновлений

`UpdateChecker` использует один shared `HttpClient` с redirect policy `NEVER`, explicit `CheckState` и асинхронно читает не более 64 KiB manifest body. Требуются status 200, JSON/plain Content-Type, строгий UTF-8, ограниченные поля, numeric version, HTTPS без user info/query/fragment, разрешённый host, точный repository path и JAR `CNDL_chat+-<version>.jar`. `UpdateVersion` отдельно сохраняет прежнюю comparison semantics. Async callback публикует только состояние/immutable DTO; экран открывается из client tick.

## Исходящие вызовы

`OutgoingChatService.MinecraftTransport` является единственной точкой прямых вызовов `sendChat`/`sendCommand`. `ServerCommandService` валидирует аргументы и разворачивает шесть templates активного snapshot. `ChatResponderEngine` и `PeriodicMessageScheduler` передают универсальные chat/command payload в outgoing service, а `FriendLookupManager` и `ResponderScreen` вызывают именованные методы command service.

## Regex-аудит

### Явный `Pattern.compile`

| Класс:строка | Назначение | Частота |
|---|---|---|
| `WildcardMatcher` | wildcard rules и muted words | при первом использовании нового source; cache экземпляра ограничен 512 entries |
| `ParserPatternValidator` / `CompiledParserSettings` | Discord, channel separators и friend lookup patterns | один раз при публикации active snapshot; ошибка одного pattern изолирована |

### `String.matches` (неявная компиляция regex)

- `ChatResponderEngine:109`: Discord detection на каждое определение канала.
- `ResponderScreen:503`: Minecraft nickname при добавлении друга.
- `ResponderScreen:553`: сумма `/pay`.
- `ResponderScreen:688`: Minecraft nickname для `/ignoreplayer`.
- `ResponderScreen:708`: Discord nickname.
- `UpdateChecker:89`: формат версии manifest.

### Replace-regex

- `ChatResponderEngine:288`: `replaceAll("\\s+", " ")` при каждой нормализации; метод вызывается многократно на сообщение/rule.
- Дополнительно `UpdateChecker:122` использует `replaceFirst("[^0-9].*$", "")` при сравнении частей версии.

## Все места сохранения config

| Класс:строка | Причина | Обработка результата |
|---|---|---|
| `FriendLookupManager:124` | завершение lookup с найденным last-seen | игнорируется |
| `ResponderScreen:329` | снять Discord-мут | игнорируется |
| `ResponderScreen:363` | удалить muted word | игнорируется |
| `ResponderScreen:489` | переключить HUD | игнорируется |
| `ResponderScreen:514` | добавить друга | игнорируется |
| `ResponderScreen:528` | удалить друга | игнорируется |
| `ResponderScreen:719` | добавить Discord-мут | игнорируется |
| `ResponderScreen:737` | добавить muted word | игнорируется |
| `ResponderScreen:832` | явная кнопка Save | статус success/error |
| `ResponderScreen:862` | закрытие/удаление экрана | игнорируется |
| `PeriodicMessageScreen:132` | Save периодических сообщений | статус success/error |

`ConfigManager.save` сначала вызывает `sanitize`, пишет `.tmp`, пробует atomic move и делает обычный move при любом `IOException` от atomic move. Причина fallback не логируется; временный файл после общего отказа явно не удаляется.

## Mutable static fields

Переназначаемые static fields:

- `GasadaChatResponderClient.CONFIG`.
- `GasadaChatResponderClient.FRIEND_LOOKUP`.
- `FriendsHud.notificationsArmed`.
- `FriendsHud.activeConnection`.
- `FriendsHud.notificationsEnabledAt`.

Static final ссылки на изменяемые контейнеры:

- `FriendsHud.previousOnline` (`HashSet`).
- `FriendsHud.offlineSince` (`LinkedHashMap`).
- `FriendsHud.onlineNotices` (`LinkedHashMap`).
- `CreditRenderer.STOPS` и `CreditRenderer.COLORS` (private arrays; фактически после инициализации не изменяются).

Остальные static application constants — строки, числа, `Pattern`, logger и Gson instances; проект не изменяет их после инициализации.

## Бизнес-логика в Screen-классах

### `ResponderScreen`

Layout четырёх вкладок остаётся в `ResponderScreen`, но повторяемые и stateful обязанности вынесены в `RulesTabController`, `ChannelsTabController`, `BlacklistTabController`, `FriendsTabController`, `PlayerSuggestionProvider`, `Pagination`, `ScreenStatus` и `UiConstants`. Screen больше не вызывает `ConfigManager` напрямую, не собирает серверные команды и не содержит lookup/Discord/presence algorithms. Переход к periodic settings выполняется видимой кнопкой «Рассылки».

- создаёт widgets четырёх существующих вкладок и связывает EditBox/CycleButton с draft view;
- использует validators PR 3 и передаёт friend actions в `FriendActionService`;
- ставит active friends в lookup queue и вычисляет только presentation-состояние открытой вкладки;
- делегирует mutations/save соответствующему tab controller.

### `PeriodicMessageScreen`

- хранит draft, парсит и валидирует интервалы и обязательность сообщения;
- заменяет runtime list до подтверждения успешной записи;
- вызывает `ConfigManager.save` и интерпретирует boolean status.

### `UpdateAvailableScreen`

- хранит mutable DTO `UpdateInfo` по ссылке и инициирует подтверждённое открытие URL; сетевой parsing/validation остаётся в `UpdateChecker`.

## Главные узкие места и риски

- Bootstrap содержит parsing/filter business logic и global mutable dependencies.
- `ResponderScreen` (911 строк) объединяет UI, validation, config, online lookup и command construction.
- Повреждённый JSON приводит к default config без backup; последующее `ResponderScreen.removed()` может перезаписать исходный повреждённый файл значениями по умолчанию.
- Значения из вручную изменённого config почти не валидируются по длине/управляющим символам; friend names и channel commands могут попасть в команды.
- `/w` и `/mail send` проверяют только непустоту UI текста; CR/LF/control characters не отклоняются. Selected friend повторно не валидируется. `/pay` допускает ноль и не ограничивает число цифр за пределами UI max length.
- Нормализация всё ещё вызывается многократно в горячем пути; wildcard regex централизованы и кешируются ограниченными instance-cache.
- Friend lookup скрывает широкий набор строк даже вне активного lookup и сохраняет config после каждого завершённого friend result.
- HUD вычисляет state transitions и запускает звук внутри render.
- Config mutations часто происходят до проверки результата save; большинство результатов save игнорируется.
- Нет тестовой инфраструктуры и автоматических characterization tests.

Минимальный следующий PR: только подключить JUnit 5 и зафиксировать текущую чистую логику `UpdateChecker.compareVersions`, `ResponderConfig.sanitize` и старые миграции без изменения runtime-поведения. Wildcard/channel/lookup algorithms пока private и завязаны на Minecraft; их тестируемое извлечение должно быть отдельными следующими PR.
