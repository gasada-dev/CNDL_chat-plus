# Участие в разработке CNDL_chat+

Перед началом прочитайте `AGENTS.md`, профильные документы в `docs/` и открытые issues.

## Лицензирование вклада

Отправляя код, документацию, тесты или другие материалы, вы подтверждаете право на вклад и
соглашаетесь лицензировать его на условиях GNU General Public License v3.0 only (`GPL-3.0-only`).
Не добавляйте код или ресурсы с несовместимой лицензией.

## Рабочий процесс

1. Создайте отдельную ветку от актуальной `main`.
2. Ограничьте pull request одной подсистемой или одной пользовательской задачей.
3. Перед изменением алгоритма добавьте characterization test.
4. Сохраняйте MOD ID, F8, адресные history/bookmark paths и inert automation bridge.
5. Для изменения хранения добавьте migration tests, byte verification и idempotence checks.
6. Выполните `./gradlew clean test build` и `git diff --check`.
7. Заполните pull request checklist и укажите ручную проверку.

Не включайте в commit `build/`, пользовательские config, логи, IDE-файлы, временные файлы и
локальные JAR. Не меняйте `UPDATE_NOTES.md`, если это не задача пользователя.

## Vanilla-box граница

Поддерживается только `server-templates/vanilla-box.json`. Не добавляйте выбор, binding,
import или configuration для другого сервера. Java-фабрики задают значения только при первой
инициализации и не заменяют сохранённую конфигурацию Vanilla-box.

Миграция архивирует retired active paths в
`cndl-chat-plus-retired-server-support-v1/` до удаления originals. Не удаляйте backup,
branded data, history или bookmarks. При отсутствующей или повреждённой инициализированной
конфигурации Vanilla-box runtime должен оставаться inactive, а не запускаться с defaults.

Серверные команды проходят validators и отправляются только через общий command service.
Не публикуйте уязвимости в обычном issue, следуйте [SECURITY.md](SECURITY.md).
