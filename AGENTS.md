# AGENTS.md — CNDL_chat+

## Scope

CNDL_chat+ — client-only Fabric-мод. Реализуйте только явно запрошенное поведение.
Без отдельной задачи сохраняйте UI, config compatibility, server-template isolation и
существующие серверные команды.

## Быстрая навигация

- Production: `src/client/java/ru/gasada/cndlchatplus/` (один flat package).
- Tests: `src/test/java/ru/gasada/cndlchatplus/`; fixtures: `src/test/resources/fixtures/`.
- Resources/templates: `src/client/resources/`; metadata: `src/client/resources/fabric.mod.json`.
- Composition root, Fabric events и tick order: `CndlChatPlusClient`.
- Карта feature → owner → tests: `docs/FEATURE_MAP.md`.
- Build truth: `gradle.properties`, `build.gradle`.

Читайте только относящиеся к задаче документы:

| Задача | Документ |
|---|---|
| Event flow, runtime, reset, threading, hot path | `docs/ARCHITECTURE.md` |
| JSON, load/save, migration, compatibility | `docs/CONFIG.md` |
| Template resolution, CRUD, catalog, import | `docs/SERVER_TEMPLATES.md` |
| Commands, placeholders, validation, privacy | `docs/SERVER_COMMANDS.md` |
| UI и ручное поведение | `docs/MANUAL_TESTS.md` |
| Version, CI, release, JAR | `docs/RELEASE.md` |

## Перед изменением

1. Проверьте worktree; не изменяйте и не откатывайте чужие правки.
2. Найдите всех production/test callers, readers и writers изменяемого кода или поля.
3. Прочитайте ближайшие tests. Перед рефакторингом алгоритма добавьте characterization test.
4. Один набор изменений ограничивайте одной подсистемой или одной пользовательской задачей.
5. Для подготовки версии, CI и публикации следуйте `docs/RELEASE.md`.

## Жёсткие контракты

- Без явной задачи и migration не меняйте MOD ID, config path/JSON fields, F8, template
  IDs/paths, `ChatChannel`, first-match-wins, максимум три periodic messages и UI behavior.
- Server-specific данные применяются только через active `ActiveTemplateSnapshot`.
- Configured default template допустим. Missing/corrupt referenced template очищает runtime;
  никогда не подставляйте скрытый fallback `Vanilla-box`.
- Сохраняйте atomic temp → move и явные ошибки I/O; не сохраняйте config из render.
- UI, connection/player list, send, HUD state и sound работают на client thread. Async HTTP
  не открывает screen напрямую.
- В message/render hot paths запрещены file I/O, HTTP и повторная компиляция regex.
- Named commands и substitutions идут через `ServerCommandService`; все sends — через
  `OutgoingChatService`. Валидируйте данные повторно непосредственно перед отправкой.
- Сохраняйте существующее поведение explicit slash commands из rules/periodic messages.
- Не логируйте private messages, email, reply payloads и amounts.
- Не подавляйте ошибки пустым `catch`; не добавляйте сторонние материалы без ясного
  происхождения и GPL-3.0-compatible лицензии.

## Стиль кода

- Отступы — табы. Один класс = один файл, flat package `ru.gasada.cndlchatplus`.
- Mixin-классы — исключение: только в `ru.gasada.cndlchatplus.mixin` (mixin config package
  не должен включать обычные классы, иначе entrypoint не загружается).
- Package-private по умолчанию; `public` только там, где нужен внешний доступ (Fabric entrypoint,
  UI, cross-feature API). Не добавляйте getters/accessors без вызовов.
- Records для DTO (`VisibilityDecision`, `PlayerLookupData` и т.п.).
- UI-строки на русском; логи без приватных данных (см. жёсткие контракты).
- Тесты JUnit 5: `ClassNameTest` зеркалит production класс; fixtures — `src/test/resources/fixtures/`.
- `docs/FEATURE_MAP.md` — источник истины feature → owner → tests; обновляйте при смене владельца.

## Проверка

Обычные изменения:

```bash
./gradlew test
./gradlew build
git diff --check
```

Для release/CI следуйте `docs/RELEASE.md`. В результате перечислите изменённые файлы,
автоматические и ручные проверки, риски и ограничения. Не включайте unrelated formatting,
`build/`, local config/log/JAR или персональные данные.
