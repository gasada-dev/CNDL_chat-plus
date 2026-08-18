# Выпуск CNDL_chat+

Любое изменение проекта, включая документацию и CI, обязано увеличивать
`mod_version`. Commit с изменениями нельзя публиковать с уже использованной версией;
одновременно обновляются `README.md` и `CHANGELOG.md`.

GitHub Release создаётся автоматически workflow `.github/workflows/release.yml` при
push тега `v<mod_version>`. Workflow повторно выполняет clean test/build, проверяет
совпадение tag и `gradle.properties`, а затем прикладывает два основных JAR:
`CNDL_chat+-<version>-mc1.21.11.jar` и `CNDL_chat+-<version>-mc26.2.jar`.
Sources JAR и обычные CI artifacts в release не попадают.

## Подготовка

1. Установить JDK 25 и проверить `git status --short`.
2. Увеличить `mod_version` в `gradle.properties`, badge/ссылки в `README.md` и
   раздел версии в `CHANGELOG.md`.
3. Выполнить сценарии `docs/MANUAL_TESTS.md` в отдельном профиле Prism Launcher с
   backup config.
4. Убедиться, что в корне репозитория нет JAR: источником загрузки является только
   asset GitHub Release.

## Локальная проверка

```bash
./gradlew clean test build assembleRelease
git diff --check
```

Проверить:

- оба JAR из `build/release/` открываются как ZIP и содержат свой `fabric.mod.json`;
- JAR `mc1.21.11` требует Java 21, а JAR `mc26.2` требует Java 25;
- JUnit отсутствует в runtimeClasspath и внутри JAR;
- старый `gasada-chat-responder.json` копируется в `cndl-chat-plus.json` и мигрирует с backup в `Vanilla-box`;
- legacy rules/periodic/reply fields сохраняются после load/save/copy и остаются inert для миграции CNDL_toolkit;
- bundled template JSON находится внутри JAR, пользовательские config/log/tmp не попали;
- при недоступности player-profile API server lookup сохраняет named fields из active template;
- при `marry: null` проверяется только настроенный в active template постраничный marriage lookup;
- tag будет иметь точное имя `v<mod_version>`.

Для версии 0.8.0 публикация CNDL_chat+ не меняет workflow: push main выполняется позже без
release/tag. Пользовательский порядок migration: CNDL_chat+ 0.8.0 запускается первым,
затем устанавливается приватный [CNDL_toolkit 0.1.0](https://github.com/gasada-dev/CNDL_toolkit/releases/tag/v0.1.0).
F8 открывает chat manager, F9 — toolkit.

## Публикация

После commit и ручной проверки:

```bash
git tag v<mod_version>
git push origin main
git push origin v<mod_version>
```

Подставьте фактическую версию. Release workflow использует `contents: write`,
`actions/checkout@v4`, `actions/setup-java@v4` с Java 25 и GitHub CLI
`gh release create --verify-tag --generate-notes --latest`. Если tag не равен
`v<mod_version>`, один из двух JAR отсутствует или тесты не прошли, release не создаётся.

После завершения открыть `https://github.com/gasada-dev/CNDL_chat-plus/releases/latest`
и проверить оба asset. `UpdateChecker` использует этот же latest release через GitHub REST API;
отдельный `version.json` больше не используется. Автоматической установки обновления в
папку mods нет: пользователь подтверждает открытие ссылки и скачивает JAR сам.
