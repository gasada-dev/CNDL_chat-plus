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
- старый `gasada-chat-responder.json` мигрирует с backup в `Vanilla-box`;
- bundled template JSON находится внутри JAR, пользовательские config/log/tmp не попали;
- при недоступности player-profile API server lookup сохраняет named fields из active template;
- при `marry: null` проверяется только настроенный в active template постраничный marriage lookup;
- tag будет иметь точное имя `v<mod_version>`.

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

После завершения открыть `https://github.com/gasada-dev/MineModChat-/releases/latest`
и проверить оба asset. `UpdateChecker` использует этот же latest release через GitHub REST API;
отдельный `version.json` больше не используется. Автоматической установки обновления в
папку mods нет: пользователь подтверждает открытие ссылки и скачивает JAR сам.
