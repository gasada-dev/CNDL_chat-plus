# Серверные команды и исходящие сообщения

`OutgoingChatService.MinecraftTransport` является единственной точкой вызова Minecraft
`sendChat` и `sendCommand`. Перед постановкой и непосредственно перед отправкой сервис
проверяет Vanilla-box gate, connection, ограничения ввода и текущую runtime generation.
Команда передаётся API без начального `/`.

## Команды Vanilla-box

| Пользовательский вид | Метод | Placeholders |
| --- | --- | --- |
| `/ignoreplayer <player>` | `ServerCommandService.ignorePlayer` | `{player}` |
| `/clan lookup <player>` | `ServerCommandService.lookupFriend` | `{player}` |
| `/w <player> <message>` | `ServerCommandService.privateMessage` | `{player}`, `{message}` |
| `/pay <player> <amount>` | `ServerCommandService.pay` | `{player}`, `{amount}` |
| `/call <player>` | `ServerCommandService.call` | `{player}` |
| `/mail send <player> <message>` | `ServerCommandService.mail` | `{player}`, `{message}` |
| `/tpaccept` | `ServerCommandService.acceptTeleport` | Нет |
| `/ps add <player>` и `/ps remove <player>` | protection actions | `{player}` |
| `/vm trusted add <player>` и `/vm trusted remove <player>` | trader actions | `{player}` |
| `/claimfly` и `/enderchest` | F7 и `\` | Нет |
| `/marry kiss`, `/marry home`, `/marry tp` | marriage actions | Нет |

Строки определены `ServerCommandSettings.vanillaBoxDefaults()` только для первой инициализации
`server-templates/vanilla-box.json`. `CommandTemplateValidator` проверяет точный набор
placeholders, а `PlayerNameValidator`, `MessageValidator`, `AmountValidator` и
`InputSanitizer` повторно проверяют аргументы перед отправкой. Пустая, invalid или недоступная
команда ничего не отправляет. Fallback к bundled JSON или другой конфигурации отсутствует.

F7 и `\` являются обычными переназначаемыми key mappings и работают только при закрытом GUI,
разрешённом Vanilla-box connection и активном runtime. `Alt+ПКМ` по игроку открывает меню
Vanilla-box действий только в тех же условиях.

## Отсутствующая настройка

Команды, канальные форматы и parser settings не редактируются через UI. В моде нет editor,
template import, alternate server configuration или маршрута выбора команды для другого
сервера. Placeholder templates в коде остаются внутренним форматом безопасного построения
команд, а не пользовательским механизмом выбора сервера.

## Lookup, телепорт и privacy

Friend lookup использует одну конфигурацию Vanilla-box и заранее скомпилированные parsers.
`teleportRequestPattern` требует capture group 1 с валидным ником, а политика автоприёма
использует друзей из той же конфигурации. Очереди и pending results очищаются при disconnect,
denied join и замене runtime.

CNDL_chat+ не формирует auto-reply и periodic messages. Их persisted поля остаются inert bridge
для CNDL_toolkit. Не логируйте private/mail text, reply payloads, email или суммы.
