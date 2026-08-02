# Серверные команды и исходящие сообщения

Документ фиксирует все существующие пути отправки версии 0.4.3. Команды передаются в Minecraft API без начального `/`; в таблицах показан пользовательский вид с `/`.

## Именованные команды

| Команда | Отправитель | Ожидаемые аргументы | Фактическая валидация перед отправкой | Текущие пределы | Ожидаемый ответ/эффект | Что скрывает мод |
|---|---|---|---|---|---|---|
| `/ignoreplayer <player>` | `ResponderScreen.addMutedPlayer` | Minecraft nickname | trim; `[A-Za-z0-9_]{1,16}`; наличие connection | UI field max 32, regex фактически 1..16; total command length отдельно не проверяется | Сервер включает/выключает ignore player по своим правилам | Специального фильтра ответа нет |
| `/clan lookup <player>` | `FriendLookupManager.tick` | friend name из queue | Только null/blank и case-insensitive queue dedup до постановки; повторной проверки nickname/control chars нет | Не ограничен manager; UI add friend ограничен 16, но ручной config обходит UI | Многострочный профиль/последний вход | Пустые/timestamp-only; `Был в сети/онлайн`, `Неактивен`, `Тип убийства` и широкий `LOOKUP_OUTPUT`; при pending также строки с friend name |
| `/w <player> <message>` | `ResponderScreen.sendPrivateToFriend` | выбранный friend, личный текст | friend должен быть выбран; connection; message trim и non-empty. Ник повторно не валидируется, control chars не проверяются | UI message max 220; total command/Minecraft limit не проверяется | Сервер отправляет личное сообщение | Ответ сервера специально не скрывается |
| `/pay <player> <amount>` | `ResponderScreen.payFriend` | выбранный friend, amount | friend selected; connection; comma→dot; `[0-9]+(?:\.[0-9]{1,2})?` | UI max 16 chars; число digits явно не ограничено; ноль допускается | Сервер выполняет/отклоняет перевод | Ответ сервера специально не скрывается |
| `/call <player>` | `ResponderScreen.callFriend` | выбранный friend | Только friend selected и connection; nickname повторно не валидируется | Отдельного лимита нет | Сервер отправляет teleport request | Ответ сервера специально не скрывается |
| `/mail send <player> <message>` | `ResponderScreen.mailFriend` | выбранный friend, mail text | friend selected; connection; message trim и non-empty. Ник/control chars не проверяются | UI message max 220; total limit не проверяется | Сервер отправляет offline mail | Ответ сервера специально не скрывается |

## Другие command/chat paths

### Ответ автоответчика

`ChatResponderEngine.sendReply` строит `finalOutgoing` из `ReplyRule.response` и полей `globalPrefix`, `clanReplyPrefix`, `privateReplyCommand`.

- Строка с `/` → `sendCommand(finalOutgoing.substring(1))`.
- Остальная строка → `sendChat(finalOutgoing)`.
- Отправка выполняется через `minecraft.execute` после повторной проверки connection.
- `recordOutgoing` сохраняет полный текст для 5-секундной защиты от цикла.

UI ограничивает response 256 символами, prefixes 16/32/64, но загруженный вручную config не валидируется по длине/control chars. В LOCAL rule пользовательский response, начинающийся `/`, становится произвольной серверной командой. В PRIVATE rule настраиваемый `privateReplyCommand` также конкатенируется с response. Это существующая универсальность, но одновременно command-injection boundary.

### Периодическое сообщение

`PeriodicMessageScheduler.send` принимает `PeriodicMessageConfig.message.trim()`:

- leading `/` → `sendCommand(outgoing.substring(1))`;
- иначе → `sendChat(outgoing)`.

UI ограничивает текст 256 символами, требует non-empty для enabled slot и interval 1..525600. Загруженный вручную JSON обходит length/control-character validation. Строка `/` отдельно не отклоняется и превращается в пустой command argument.

## Полный список прямых Minecraft API calls

| Место | Вызов |
|---|---|
| `ChatResponderEngine:244` | `sendCommand(finalOutgoing.substring(1))` |
| `ChatResponderEngine:246` | `sendChat(finalOutgoing)` |
| `PeriodicMessageScheduler:55` | `sendCommand(outgoing.substring(1))` |
| `PeriodicMessageScheduler:57` | `sendChat(outgoing)` |
| `FriendLookupManager:70` | `sendCommand("clan lookup " + pendingFriend)` |
| `ResponderScreen:542` | `sendCommand("w " + selectedFriend + " " + message)` |
| `ResponderScreen:557` | `sendCommand("pay " + selectedFriend + " " + amount)` |
| `ResponderScreen:565` | `sendCommand("call " + selectedFriend)` |
| `ResponderScreen:578` | `sendCommand("mail send " + selectedFriend + " " + message)` |
| `ResponderScreen:698` | `sendCommand("ignoreplayer " + nickname)` |

## Пользовательские/config-данные, попадающие в команды

| Данные | Источник | Куда попадают | Защита сейчас |
|---|---|---|---|
| Rule response | `rules[].response` | command или chat | blank skip, UI max 256; load не валидирует |
| Global/clan/private prefix | config/channel fields | префикс ответа, иногда command | blank checks, UI max; load не очищает controls |
| Periodic text | `periodicMessages[].message` | command или chat | trim/blank/interval checks; no control validation |
| Lookup nickname | `friends` → queue | `/clan lookup` | только blank/dedup в queue |
| Selected friend | `friends` | `/w`, `/pay`, `/call`, `/mail send` | проверяется выбор, но не nickname format перед send |
| Private/mail text | UI edit boxes | `/w`, `/mail send` | trim/non-empty, UI max 220 |
| Amount | UI edit box | `/pay` | comma normalization + decimal regex; ноль разрешён |
| Ignore nickname | UI edit box | `/ignoreplayer` | строгий Minecraft nickname regex |

## Рекомендации для следующего command-hardening этапа

Без добавления новых команд нужна одна точка отправки и одна точка формирования перечисленных команд. Валидация должна выполняться непосредственно перед send, даже если значение уже проверял UI:

- player `[A-Za-z0-9_]{1,16}`;
- отсутствие CR/LF/NUL и ненужных control characters;
- non-empty и protocol/server length limits;
- `/` не является валидной periodic command;
- amount положительный, не exponent, не `NaN`/`Infinity`, максимум две дробные цифры и ограниченное число digits;
- Send result возвращается UI/manager, а sensitive message/amount не пишутся в обычный log.

До реализации эти правила должны быть characterization/security tests; текущий документ не меняет runtime-поведение.
