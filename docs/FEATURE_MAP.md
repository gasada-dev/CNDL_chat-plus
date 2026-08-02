# Карта существующих функций

Таблица отражает фактическую реализацию версии 0.4.3. Она не является перечнем будущих функций.

| Функция | Основной класс/метод | Связанные классы | Config-поля | Ручная проверка |
|---|---|---|---|---|
| Загрузка клиента | `GasadaChatResponderClient.onInitializeClient` | все сервисы, Fabric events | весь config | Запустить client, войти на сервер, проверить отсутствие startup error |
| Открытие GUI по F8 | `GasadaChatResponderClient` END_CLIENT_TICK | `ResponderScreen` | — | Нажать F8 в игре; экран открывается и не ставит игру на паузу |
| Включение автоответчика | rules tab `ResponderScreen` | `ChatResponderEngine` | `enabled` | Выключить, получить trigger, убедиться в отсутствии ответа; включить обратно |
| CRUD rules и порядок | `ResponderScreen.initRulesTab/addRuleRow` | `ReplyRule`, `ConfigManager` | `rules` | Создать два совпадающих rules; отвечает только первый; удалить/отключить rule |
| Wildcard rule `*` | `WildcardMatcher` (`FULL_MATCH`) | `ChatResponderEngine`, `CompiledWildcard`, `ReplyRule` | `rules[].trigger` | Проверить exact, prefix*, *suffix, *contains*, `*` |
| Определение канала | `ChatChannelDetector` | `CompiledParserSettings`, `DiscordMessageParser`, `ChatResponderEngine` | active template prefixes/markers/patterns | Отправить LOCAL/GLOBAL/CLAN/PRIVATE/Discord samples и проверить канал ответа |
| Кандидаты текста | `ReplyCandidateBuilder` | `ChatResponderEngine` | active prefix и separators | Проверить decorated server messages после server-specific separators |
| Игнорирование своих сообщений | `OwnMessageGuard` | `ChatResponderEngine`, Minecraft profile/connection | active channel prefixes | Ответ мода не должен повторно активировать rule в течение 5 секунд |
| Защита от дублей | `DuplicateMessageGuard` | `ChatResponderEngine` | — | Два одинаковых callback в пределах 400 мс дают один ответ |
| Полное скрытие Discord | `ChatVisibilityFilter` | `CompiledFilterSet`, `DiscordMessageParser` | active template `discordChatEnabled` | Выключить toggle; Discord-сообщение скрыто и не запускает rule |
| Распознавание Discord sender | `DiscordMessageParser` | `CompiledParserSettings` | active template Discord patterns | Проверить поддерживаемые круглые/квадратные/угловые варианты marker и `»` |
| Локальный Discord-мут | `ResponderScreen.addDiscordMutedPlayer` | Discord filter | `discordMutedPlayers` | Добавить Unicode name, получить сообщение, затем снять мут |
| Чёрный список слов | `ChatVisibilityFilter`, `CompiledFilterSet` | `WildcardMatcher` (`CONTAINS_MATCH`), blacklist tab | active template `mutedWords` | Проверить substring без `*`, glob с `*`, регистр, Unicode и удаление item |
| Серверный мут игрока | `ServerCommandService.ignorePlayer` | `ResponderScreen`, `OutgoingChatService` | active template command | Валидный ник отправляет `/ignoreplayer`; невалидный не отправляет |
| Список друзей | friends tab `ResponderScreen` | `ConfigManager` | `friends`, `friendLastSeen` | Добавить/выбрать/удалить друга, закрыть и открыть экран |
| Подсказки ников | `refreshSuggestions`, `refreshFriendSuggestions` | `PlayerInfo` | — | Ввести prefix online player и нажать Tab |
| Online/offline во вкладке | `ResponderScreen.tick/currentOnlineFriends` | connection player list | `friends`, `friendLastSeen` | Открыть вкладку; дождаться обновления примерно через 20 ticks |
| Lookup последнего входа | `FriendLookupManager` | `FriendLookupParser`, `ServerCommandService`, Fabric ALLOW events | active template patterns; `friends`, `friendLastSeen` | Дождаться `/clan lookup`, проверить скрытие блока и сохранённый timestamp |
| HUD online-друзей | `FriendsHud.render` | `FriendPresenceTracker`, `FriendHudSnapshot`, HUD registry | active template friends/HUD | Включить HUD; online friend виден справа снизу; выключить toggle |
| Уведомление/звук входа | `FriendPresenceTracker`, `FriendsHud.tick` | `FriendHudSnapshot`, `SoundEvents.PLAYER_LEVELUP` | active template HUD/sound | После warmup подтвердить offline ≥5 с, затем online; одно notice на 4 с и один звук |
| Личное сообщение другу | `ServerCommandService.privateMessage` | `ResponderScreen`, `OutgoingChatService` | active template command/friends | Выбрать друга, ввести текст, нажать «Отправить ЛС», проверить `/w` |
| Перевод другу | `ServerCommandService.pay` | `ResponderScreen`, `AmountValidator` | active template command/friends | Проверить положительную сумму, десятичную с `,`, invalid format и отправку `/pay` |
| Запрос телепорта | `ServerCommandService.call` | `ResponderScreen`, `OutgoingChatService` | active template command/friends | Выбрать друга, нажать «Отправить ТП», проверить `/call` |
| Почта другу | `ServerCommandService.mail` | `ResponderScreen`, `MessageValidator` | active template command/friends | Ввести независимый mail text, проверить `/mail send` и очистку поля |
| До трёх рассылок | `PeriodicMessageScreen` | config, scheduler | `periodicMessages` | Открыть скрытую область в (0,0) rules screen; добавить 3, четвёртая недоступна |
| Планирование рассылки | `PeriodicMessageScheduler.tick` | `ChatResponderEngine.recordOutgoing` | `periodicMessages` | Первый send только после полного interval; edit/disable сбрасывает timer |
| Periodic chat/command | `PeriodicMessageScheduler.send` | connection | `periodicMessages[].message` | Plain text идёт как chat; leading `/` — как command |
| Загрузка/санитизация config | `ConfigManager.load`, `ResponderConfig.sanitize` | Gson | все поля | Проверить отсутствующий, старый, null-filled и вручную отредактированный JSON |
| Атомарное сохранение config | `ConfigManager.save` | NIO Files | все поля | Save создаёт итоговый JSON; `.tmp` не остаётся после успешного move |
| Legacy periodic migration | `ResponderConfig.sanitize` | `PeriodicMessageConfig` | три legacy periodic поля | Загрузить JSON только со старыми полями; появляется один slot |
| Legacy default rules migration | `ResponderConfig.hasOldDefaultRules` | `ReplyRule` | `rules` | Старую точную пару rules заменить на текущий default rule |
| Проверка обновлений | `UpdateChecker` | `UpdateAvailableScreen` | — | Войти на сервер; manifest проверяется один раз асинхронно, network failure не ломает мод |
| Валидация download URL | `UpdateChecker.isValid` | `URI`, manifest DTO | — | HTTP/другой host/неверное JAR name отклоняются; правильный raw GitHub URL принимается |
| Сравнение версий | `UpdateChecker.compareVersions` | — | — | Проверить newer/equal и trailing zero |
| Подтверждение ссылки | `UpdateAvailableScreen.init` | `ConfirmLinkScreen` | — | «Скачать» сначала открывает стандартное подтверждение URL |
| Фирменная подпись | `CreditRenderer.draw` | все screens | — | На основном, periodic и update screens виден прежний gradient CNDL |

## Класс → ответственность в текущем коде

| Класс | Фактическая ответственность |
|---|---|
| `GasadaChatResponderClient` | bootstrapping, global dependencies, key/tick/message events, Discord parse и visibility filter, muted wildcard matching |
| `ConfigManager` | JSON load/save и temp→move |
| `ResponderConfig` | data model, defaults, sanitization и legacy migrations |
| `ChatResponderEngine` | own/duplicate guards, normalization, Discord/channel detection, candidate building, wildcard rule matching, reply construction и send |
| `FriendLookupManager` | queue/state/timing, command send, response parsing, visibility decision, config mutation/save |
| `FriendsHud` | presence state machine, sound, snapshot calculation и render |
| `PeriodicMessageScheduler` | три timers и direct chat/command send |
| `ResponderScreen` | четыре tabs, live config mutation, validation, commands, friends/online/suggestions, pagination и save |
| `PeriodicMessageScreen` | periodic drafts, validation, config mutation/save и UI |
| `UpdateChecker` | async HTTP, manifest/URL/version validation и tick handoff to UI |
| `UpdateAvailableScreen` | update presentation и confirmed link opening |
| `CreditRenderer` | CNDL signature rendering |

Подробный аудит вызовов, regex, tick/render, сохранений и static state находится в `docs/ARCHITECTURE.md`.
