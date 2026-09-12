# Конфигурация CNDL_chat+

## Активные файлы

| Путь | Назначение |
| --- | --- |
| `.minecraft/config/cndl-chat-plus.json` | Глобальные настройки и совместимое представление Vanilla-box. |
| `.minecraft/config/server-templates/vanilla-box.json` | Единственный источник серверных настроек. |
| `.minecraft/config/cndl-chat-plus-chat-history/<server>.json` | История для нормализованного разрешённого адреса. |
| `.minecraft/config/cndl-chat-plus-chat-bookmarks/<server>.json` | Закладки для нормализованного разрешённого адреса. |

`server-templates/vanilla-box.json` хранит каналы, фильтры, Discord-муты, друзей,
`friendLastSeen`, политику автоприёма телепорта, команды и parser settings. Существующий
корректный файл имеет приоритет. Это единственная server-specific authority, поэтому адрес,
root config, default, binding или другой JSON не могут выбрать иную конфигурацию.

`ServerCommandSettings.vanillaBoxDefaults()` и `ParserSettings.vanillaBoxDefaults()` служат
только значениями первой инициализации из совместимого config. Bundled catalog и bundled
Vanilla-box JSON в JAR отсутствуют. После инициализации нет fallback: отсутствующий или
повреждённый файл оставляет runtime inactive.

## Совместимое представление и automation bridge

`cndl-chat-plus.json` сохраняет глобальные переключатели чата, истории, закладок, HUD, звуков,
Chat Alerts, бинды и совместимое видимое представление Vanilla-box. Сохранение использует
atomic temporary file и move.

Поля `enabled`, `rules`, `periodicMessages`, legacy periodic singleton, `clanReplyPrefix` и
`privateReplyCommand` остаются inert automation bridge. CNDL_chat+ не показывает и не исполняет
автоответы или periodic automation. Nullable-коллекции, вложенные `null`, порядок, количество,
тексты и интервалы bridge не нормализуются, чтобы CNDL_toolkit мог мигрировать их без потерь.

`storageVersion` имеет nullable migration semantics: отсутствие, JSON `null` и `0` означают,
что переход ещё нужен. Значение `1` записывается только после сохранения Vanilla-box, проверки
архива и удаления перечисленных старых оригиналов. При `1` мод никогда не создаёт отсутствующий
или повреждённый `vanilla-box.json` из defaults.

## Миграция и архив

Перед cleanup мод читает основной JSON без изменения bridge, сохраняет и перечитывает
`server-templates/vanilla-box.json`, затем создаёт или проверяет фиксированный архив:

```text
.minecraft/config/cndl-chat-plus-retired-server-support-v1/
  manifest.json
  files/<relative-path>
```

Manifest содержит детерминированный отсортированный список `{path,size,sha256}` с lowercase
SHA-256. Каждый raw file копируется в `files/<relative-path>` через temporary file и atomic move,
после чего сравниваются путь, длина, digest и bytes. Уже совпадающий архив позволяет продолжить
прерванный запуск. Коллизия, source drift, symlink, special file, лишняя запись или ошибка
проверки прекращают cleanup без удаления оставшихся source-файлов.

После успешной проверки удаляются только manifest-listed originals:

| Путь | После успешной миграции |
| --- | --- |
| `server-templates.json` | Архивирован, затем удалён. |
| `server-templates/*.json`, кроме `vanilla-box.json` | Архивирован, затем удалён. |
| `cndl-chat-plus-template-imports/**` | Архивирован, затем удалён. |
| `cndl-chat-plus-retired-server-support-v1/**` | Остаётся неизменяемым. |
| `cndl-chat-plus.legacy-backup.json` | Остаётся без изменений. |
| `gasada-chat-responder.json` и другие branded sources | Остаются без удаления и перезаписи. |
| `gasada-chat-responder-template-imports/**` | Остаются без изменений и больше не копируются. |
| История и закладки | Остаются с прежней address-scoped семантикой. |

Оригиналы удаляются только после byte verification. Marker `storageVersion: 1` пишется последним,
поэтому повторный запуск проверяет тот же архив и не создаёт дубликаты. Повреждённый основной
config, архив или обязательный Vanilla-box config приводит к явной I/O ошибке и inactive runtime.

## Пользовательские данные

История записывается только для разрешённого multiplayer address, восстанавливается при join и
не создаётся в singleplayer. Закладки независимы от истории, тоже разделены по адресу и пишутся
атомарно после явного действия. Повреждённый history/bookmark JSON не завершает клиент аварийно,
но соответствующий runtime list будет пустым.

Глобальные Chat Alerts сопоставляют только видимые после фильтрации сообщения. Их регулярные
выражения компилируются при сохранении, а не в message hot path. Private messages, email,
reply payloads и суммы не должны попадать в логи.

## Границы UI

Нет UI для создания, копирования, выбора, редактирования, default, binding, resolve, catalog или
import server configurations. Нет editable UI для команд или parser settings. Фабрики Java не
являются пользовательской конфигурацией и не перезаписывают существующие данные.
