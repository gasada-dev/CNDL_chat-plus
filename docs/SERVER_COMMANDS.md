# Серверные команды и исходящие сообщения

`OutgoingChatService.MinecraftTransport` — единственная точка Minecraft API `sendChat`/`sendCommand`. Перед постановкой на client thread сервис проверяет connection, trim, controls и общий предел 256; непосредственно в Runnable connection проверяется повторно. Команды передаются API без начального `/`.

## Именованные команды active template

| Пользовательский вид Vanilla-box | Метод | Placeholders | Валидация непосредственно перед send |
|---|---|---|---|
| `/ignoreplayer <player>` | `ServerCommandService.ignorePlayer` | `{player}` | `PlayerNameValidator`, `CommandTemplateValidator`, connection |
| `/clan lookup <player>` | `ServerCommandService.lookupFriend` | `{player}` | player/template/connection; вызывается через `FriendActionService` |
| `/w <player> <message>` | `ServerCommandService.privateMessage` | `{player}`, `{message}` | player, `MessageValidator.PRIVATE_MESSAGE` (220), template, outgoing |
| `/pay <player> <amount>` | `ServerCommandService.pay` | `{player}`, `{amount}` | player, positive `BigDecimal`, comma→dot, max 2 decimals, no exponent/NaN/Infinity |
| `/call <player>` | `ServerCommandService.call` | `{player}` | player/template/connection |
| `/mail send <player> <message>` | `ServerCommandService.mail` | `{player}`, `{message}` | player, `MessageValidator.MAIL` (220), template, outgoing |
| `/marry list <page>` (Vanilla-game) | `ServerCommandService.marriageList` | `{page}` | page 1–1000, template, connection |
| `/tpaccept` (Vanilla-box/Vanilla-game) | `ServerCommandService.acceptTeleport` | нет | template, connection |
| `/ps add <player>` (Vanilla-box) | `ServerCommandService.addToProtection` | `{player}` | player/template/connection |
| `/ps remove <player>` (Vanilla-box) | `ServerCommandService.removeFromProtection` | `{player}` | player/template/connection |
| `/vm trusted add <player>` (Vanilla-box) | `ServerCommandService.addTraderTrusted` | `{player}` | player/template/connection |
| `/vm trusted remove <player>` (Vanilla-box) | `ServerCommandService.removeTraderTrusted` | `{player}` | player/template/connection |

Vanilla strings определены только в `ServerCommandSettings.vanillaBoxDefaults()`. Другие classes не конкатенируют эти команды. `CommandTemplateValidator` требует точный набор placeholders и хранение без leading `/`. Если active template не содержит команду или template invalid, сервис возвращает понятную ошибку и ничего не отправляет; скрытого fallback Vanilla-box нет.

Страница «Команды» в `TemplateEditorScreen` позволяет для каждого template заменить,
например, `w {player} {message}` на `msg {player} {message}`, `call {player}` на
`tpa {player}` или `ignoreplayer {player}` на серверный аналог. Пользователь вводит
команду без начального `/` и не меняет обязательные placeholders. Discord marker/name
находятся на соседней странице; legacy private reply prefix в CNDL_chat+ не редактируется.

Во вкладке друзей `CommandTemplateDisplay` строит подсказки из immutable command snapshot
активного шаблона. Поэтому UI показывает фактические `/w`/`/msg`, `/call`/`/tpa`, pay и
mail templates, заменяя placeholders понятными словами, и не содержит скрытой команды
Vanilla-box. Пустой template отображается как «команда не настроена».

При active ID `vanilla-box` `Alt+ПКМ` по player entity открывает `NearbyPlayerMenuScreen`.
Четыре действия используют hit target, а не текст или ближайший ник. Callback возвращает `FAIL`,
поэтому обычное взаимодействие и packet на сервер не выполняются до выбора пункта меню.

`PlayerNameValidator` принимает только `[A-Za-z0-9_]{1,16}`. `InputSanitizer`/message validators отклоняют CR, LF, NUL и control/format/line/paragraph characters. Sensitive private/mail text и суммы не логируются.

## Automation boundary

CNDL_chat+ 0.8.0 не формирует и не отправляет replies/periodic messages. Их persisted поля
остаются inert migration bridge для CNDL_toolkit. `OutgoingChatService` создаётся с no-op
recorder и обслуживает только именованные server commands/context actions CNDL_chat+.

## Parser/lookup boundaries

Friend lookup и marriage list commands берутся из active template и отправляются
`ServerCommandService`; ответы разбираются active compiled patterns. Lookup брака
используется только при отсутствии `marry` в успешном API-профиле, точном active ID
`vanilla-game`, command `{page}`, regex пары с двумя никами и regex current/max page. Общий
`ServerLookupCoordinator` сериализует friend/manual/marriage запросы и очищается при
disconnect/switch. Parser извлекает данные до скрытия служебной строки.

`teleportRequestPattern` active template требует capture group 1 с валидным Minecraft-ником.
Политика active template может автоматически принять запрос от всех, друзей или выбранных
друзей. Успешный автоприём не создаёт HUD-кнопку; несовпавший запрос или ошибка отправки создаёт
временную ручную кнопку. Команда в обоих случаях берётся только из `acceptTeleport`.

## При добавлении или изменении команды

Нужно одновременно обновить `ServerCommandSettings`, `CommandTemplateValidator.CommandType`, command service tests, import validation, этот документ и manual tests. Нельзя добавлять fallback, прямой Minecraft send или логирование полного sensitive payload.
