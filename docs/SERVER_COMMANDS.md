# Серверные команды и исходящие сообщения

Документ фиксирует все существующие пути отправки. `ServerCommandService` строит именованные команды из `ActiveTemplateSnapshot.commands`, а `OutgoingChatService` является единственной точкой вызова Minecraft API. Команды передаются API без начального `/`; в таблицах показан пользовательский вид с `/`.

## Именованные команды

| Команда | Отправитель | Ожидаемые аргументы | Фактическая валидация перед отправкой | Текущие пределы | Ожидаемый ответ/эффект | Что скрывает мод |
|---|---|---|---|---|---|---|
| `/ignoreplayer <player>` | `ServerCommandService.ignorePlayer` | Minecraft nickname | `PlayerNameValidator`, command-template validation, connection | player 16; outgoing 256 | Сервер включает/выключает ignore player по своим правилам | Специального фильтра ответа нет |
| `/clan lookup <player>` | `ServerCommandService.lookupFriend` | friend name из queue | `PlayerNameValidator`, command-template validation, connection | player 16; outgoing 256 | Многострочный профиль/последний вход | Текущие lookup patterns |
| `/w <player> <message>` | `ServerCommandService.privateMessage` | выбранный friend, личный текст | player + `MessageValidator`, controls, connection | message 220; outgoing 256 | Сервер отправляет личное сообщение | Ответ сервера специально не скрывается |
| `/pay <player> <amount>` | `ServerCommandService.pay` | выбранный friend, amount | player + `AmountValidator`; положительный `BigDecimal`, comma→dot | amount input 16; outgoing 256 | Сервер выполняет/отклоняет перевод | Ответ сервера специально не скрывается |
| `/call <player>` | `ServerCommandService.call` | выбранный friend | `PlayerNameValidator`, template validation, connection | player 16; outgoing 256 | Сервер отправляет teleport request | Ответ сервера специально не скрывается |
| `/mail send <player> <message>` | `ServerCommandService.mail` | выбранный friend, mail text | player + `MessageValidator`, controls, connection | message 220; outgoing 256 | Сервер отправляет offline mail | Ответ сервера специально не скрывается |

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

## Прямые Minecraft API calls

| Место | Вызов |
|---|---|
| `OutgoingChatService.MinecraftTransport` | `sendCommand(command)` |
| `OutgoingChatService.MinecraftTransport` | `sendChat(message)` |

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
