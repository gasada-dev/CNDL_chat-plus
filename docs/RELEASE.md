# Выпуск CNDL_chat+

Релиз выполняется вручную. GitHub Actions только запускает тесты, сборку и сохраняет artifacts; workflow не создаёт tag, GitHub Release и не публикует файлы.

## Подготовка

1. Установить JDK 25 и проверить чистый `git status --short`.
2. Обновить `mod_version` в `gradle.properties`, badge/ссылки в `README.md`, `CHANGELOG.md` и `version.json`.
3. Имя download JAR обязано быть `CNDL_chat+-<version>.jar`; URL manifest — точный raw GitHub path репозитория `gasada-dev/MineModChat-`.
4. Выполнить сценарии `docs/MANUAL_TESTS.md` в отдельном профиле Prism Launcher с backup config.

## Проверка

```bash
./gradlew clean test build
git diff --check
```

Проверить:

- основной JAR в `build/libs/` открывается как ZIP и содержит `fabric.mod.json`;
- JUnit отсутствует в `runtimeClasspath` и внутри JAR;
- старый `gasada-chat-responder.json` мигрирует с backup в `Vanilla-box`;
- новый и старый профили не содержат `.tmp` после успешной записи;
- `version.json` указывает на существующий JAR той же версии.

## Публикация

После ручной проверки создать commit/tag/release обычным процессом проекта и приложить основной JAR, не `sources` JAR. Не включать пользовательские configs, `build/`, logs или корневой JAR от другой версии. Автоматическая установка обновлений модом не поддерживается.
