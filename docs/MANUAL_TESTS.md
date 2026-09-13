# Ручные проверки CNDL_chat+

Сценарии проверяют Minecraft 1.21.11 и 26.2. Используйте тестовый аккаунт и безопасные команды:
`/pay`, `/mail send` и `/ignoreplayer` меняют серверное состояние. F8 принадлежит CNDL_chat+,
F9 и automation принадлежат CNDL_toolkit.

## 1. Первый запуск и migration

1. Сделайте отдельную копию config tree с `cndl-chat-plus.json`, `server-templates.json`, одним
   non-Vanilla JSON в `server-templates/` и файлом в `cndl-chat-plus-template-imports/`.
2. Запустите клиент с этой копией.
3. Проверьте `server-templates/vanilla-box.json` и
   `cndl-chat-plus-retired-server-support-v1/manifest.json`.
4. Проверьте raw copies в `files/<relative-path>` и совпадение path, size и lowercase SHA-256 с
   manifest.
5. Проверьте удаление только `server-templates.json`, non-Vanilla JSON и active imports.
6. Проверьте сохранность `vanilla-box.json`, `cndl-chat-plus.legacy-backup.json`, branded config,
   branded imports, history и bookmarks.
7. Проверьте `storageVersion: 1` в основном config.

Ожидается: archive создаётся и проверяется до cleanup. Automation bridge с null values, порядком
и количеством остаётся без изменений. Java defaults появляются только при первой инициализации,
не заменяя существующий Vanilla-box file.

## 2. Второй запуск и fail-inactive

1. Запустите клиент с результатом сценария 1 второй раз.
2. Сравните manifest и archive tree с первым запуском.
3. Удалите или повредите `server-templates/vanilla-box.json`, оставив `storageVersion: 1`.
4. Подключитесь к разрешённому Vanilla-box address.

Ожидается: второй запуск не создаёт duplicate archive, не переписывает archive и не возвращает
retired files в active paths. В последнем случае runtime inactive: нет server commands, filters,
history/bookmark scope, HUD actions и hidden fallback.

## 3. Граница Vanilla-box

1. Подключитесь к `vanilla-box.ru`, `mc.vanilla-box.ru:25566` и `VANILLA-BOX.RU.`.
2. На каждом адресе проверьте F8, одну безопасную команду, chat filter и history/bookmark scope.
3. Подключитесь к `vanilla-box.ru.example.org`, `notvanilla-box.ru`, `127.0.0.1`, `[::1]`,
   `vanilla-box.ru:0`, адресу с пробелом и адресу с двумя конечными точками.
4. Повторите в singleplayer и при direct connection без `ServerData`.

Ожидается: разрешены apex и настоящие subdomain с корректным optional port. Case и одна trailing
dot нормализуются. Lookalike, unrelated host, IP, malformed input и singleplayer inactive.
На denied path F8 server functions не открываются, F7 и `\` ничего не отправляют, сообщения
проходят vanilla и мод не создаёт history/bookmark scope.

## 4. F8, глобальные настройки и отсутствующие маршруты

1. На разрешённом address нажмите F8.
2. Проверьте вкладки «Чёрный список» и «Друзья», первую открытую вкладку «Друзья», кнопки
   `Информация об игроке`, `⚙` и `?`.
3. Измените безопасный global toggle, закройте экран, перезапустите клиент и проверьте значение.
4. Откройте `⚙`, проверьте увеличенную кнопку в заголовке, затем проверьте, что `Chat Alerts`
   расположен рядом с переходом к «Биндам» и на широком, и на узком экране.
5. Откройте «Настройка вкладок»: скройте и снова покажите встроенную вкладку, добавьте,
   измените и удалите пользовательскую вкладку. В редакторе выберите один или несколько
   фиксированных каналов и убедитесь, что произвольного поля-маркера нет. Закройте экран,
   перезапустите клиент и проверьте сохранение списка и видимости.
6. Добавьте достаточно пользовательских вкладок и проверьте кнопки `<` и `>` в списке настроек,
   затем сузьте окно или используйте длинные названия и проверьте paging над чатом.
7. Проверьте, что нет кнопки template/import/catalog, выбора другой конфигурации,
   default/binding или настройки server commands/parsers.
8. Нажмите F7 и `\` с закрытым GUI, затем с открытым GUI.

Ожидается: global settings, пользовательские вкладки и видимость встроенных вкладок сохраняются.
`Chat Alerts` остаётся рядом с «Биндами» на обоих target. F7 и `\` отправляют не более одной
Vanilla-box команды при закрытом GUI и не отправляют её при открытом. Никакой UI route не
создаёт, не копирует, не выбирает, не импортирует и не редактирует альтернативную server
configuration.

## 5. Чат, фильтры, history и bookmarks

1. Получите global, local, clan, private, Discord и system messages.
2. Проверьте вкладки, unread counters, timestamps, Ctrl+F, context menu и duplicate collapse.
3. Добавьте muted word, Discord mute и Minecraft mute, затем проверьте, что скрытое сообщение не
   попадает в tabs или history.
4. Сохраните сообщение в bookmarks, измените только text, reconnect и проверьте его на том же
   разрешённом address.
5. Подключитесь к другому разрешённому subdomain и вернитесь к первому.
6. Откройте вкладку «ГС чат». Получите входящее сообщение с `(Войс)` и сообщение другого типа,
   затем проверьте фильтрацию. Добавьте вторую пользовательскую вкладку с источниками
   `ГС` и `Глобал`: голосовое сообщение должно быть видно в обеих вкладках, глобальное —
   только во второй.
7. В активной «ГС чат» отправьте обычный безопасный текст и проверьте на сервере маршрут `/gc`.
   Затем отправьте явную slash-команду и проверьте, что она остаётся vanilla command path.

Ожидается: history и bookmarks разделены по адресу, но используют одну Vanilla-box config.
Bookmarks независимы от history toggles. В singleplayer и denied connection bookmark UI недоступен.
Пользовательская вкладка является только фильтром представления: её ID не появляется в history,
bookmarks или Chat Alerts. При inactive runtime её outgoing prefix не имеет vanilla fallback.

## 6. Друзья, lookup, телепорт и команды

1. Добавьте valid friend, проверьте suggestions, persistence и удаление.
2. Проверьте `/clan lookup` queue, скрытие служебного ответа и last-seen persistence.
3. Проверьте private message, mail, pay, call, ignore и `Alt+ПКМ` по player entity.
4. Получите teleport request, проверьте manual button, timeout и режимы автоприёма.
5. Во время lookup или teleport request отключитесь либо перейдите на denied address.

Ожидается: все server actions используют fixed Vanilla-box commands и validators. Stale queues,
buttons, notices и callbacks очищаются. Invalid input не отправляется. Private text и суммы не
попадают в log.

## 7. VnbxBridge и оба target

1. На Minecraft 26.2 подключитесь к серверу с VnbxBridge и обновите профиль игрока.
2. Проверьте clan/marriage data, marriage HUD и stale response после disconnect.
3. На Minecraft 1.21.11 проверьте unavailable bridge и lookup fallback.
4. Повторите минимум F8, allowed/denied gate, setting persistence, history/bookmark и одну
   безопасную command path на обоих target.

Ожидается: bridge payload не появляется в обычном UI, config, history или logs. Target 1.21.11
не отправляет bridge payload.

## 8. Финальный smoke test

1. Выполните `./gradlew clean test build assembleRelease` и `git diff --check`.
2. Проверьте JAR для Minecraft 1.21.11 и 26.2 в `build/release/`.
3. На обоих target повторите migration, second-run idempotence, allowed/denied activation, F8,
    увеличенную `⚙`, размещение `Chat Alerts` рядом с «Биндами», CRUD и paging вкладок,
    мультивыбор фиксированных источников, фильтр «ГС чат» и отправку `/gc`.
4. Убедитесь, что `UPDATE_NOTES.md`, пользовательские config, archive, screenshots и `.tmp` не
   попали в commit.
