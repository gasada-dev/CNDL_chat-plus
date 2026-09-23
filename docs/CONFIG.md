# Конфигурация CNDL_chat+

## Активные файлы

| Путь | Назначение |
| --- | --- |
| `.minecraft/config/cndl-chat-plus.json` | Глобальные настройки и совместимое представление Vanilla-box. |
| `.minecraft/config/server-templates/vanilla-box.json` | Единственный источник серверных настроек. |
| `.minecraft/config/cndl-chat-plus-chat-history/<server>.json` | История для нормализованного разрешённого адреса. |
| `.minecraft/config/cndl-chat-plus-chat-bookmarks/<server>.json` | Закладки для нормализованного разрешённого адреса. |
| `.minecraft/config/cndl-chat-plus-themes/` | Пользовательские темы и `active.txt`. |

`server-templates/vanilla-box.json` хранит каналы, фильтры, Discord-муты, друзей,
`friendLastSeen`, политику автоприёма телепорта, команды и parser settings. Существующий
корректный файл имеет приоритет. Это единственная server-specific authority, поэтому адрес,
root config, default, binding или другой JSON не могут выбрать иную конфигурацию.

`ServerCommandSettings.vanillaBoxDefaults()` и `ParserSettings.vanillaBoxDefaults()` служат
только значениями первой инициализации из совместимого config. Bundled catalog и bundled
Vanilla-box JSON в JAR отсутствуют. После инициализации нет fallback: отсутствующий или
повреждённый файл оставляет runtime inactive.

## Темы

Темы находятся в `.minecraft/config/cndl-chat-plus-themes/`. При первом запуске туда копируются
все шесть bundled-тем: `default.json`, `vanilla.json`, `midnight.json`, `contrast.json`,
`sakura.json` и `nocturne-glass.json`; существующие пользовательские файлы не перезаписываются.
Активный идентификатор хранится в `active.txt`: для файла `my-theme.json` запишите `my-theme`.
Для выбора из UI имя файла и `meta.id` должны совпадать; идентификатор в `active.txt` содержит
только латинские буквы, цифры, `_` или `-`.

Полная schema JSON: `meta` — `id`, `name`, `author` (строки), `version` (число); `colors` —
`background`, `surface`, `surfaceSecondary`, `hover`, `pressed`, `border`, `borderFocused`,
`accent`, `accentSoft`, `text`, `textMuted`, `success`, `warning`, `danger`, `online`,
`hudSurface`, `noticeSurface`, `marriageSurface`, `marriageBorder`, `marriageAccent`,
`marriageText`, `chatBackground`, `chatTabBackground`, `chatTabBackgroundHover`,
`chatTabOutline`, `chatTabText`, `chatTabTextActive`, `chatBadge` (цветы `#RRGGBB` или
`#AARRGGBB`); `geometry` — `radiusSmall`, `radiusMedium`, `radiusLarge`, `borderWidth`,
`spacingSmall`, `spacingMedium`, `spacingLarge` (числа); `chat` — `padding`, `messageSpacing`,
`width`, `height`, `tabHeight`, `tabGap` (числа), `backgroundOpacity` (число),
`overrideBackground` (boolean).

Новая тема может быть короткой: отсутствующие ключи используют встроенную тему. Bundled-файлы
также содержат `_readme`; это игнорируемый loader-ом массив подсказок, который можно оставить в
своей теме. Пример `my-theme.json`:

```json
{
  "meta": {
    "id": "my-theme",
    "name": "Моя тема",
    "author": "Игрок",
    "version": 1
  },

  "_readme": [
    "Цвета: #RRGGBB или #AARRGGBB.",
    "Пропущенные ключи используют встроенную тему."
  ],

  "colors": {
    "accent": "#FF55AAFF",
    "chatBackground": "#A0181B26",
    "chatTabBackground": "#A0000000",
    "chatTabBackgroundHover": "#C0404040",
    "chatTabOutline": "#FF55AAFF",
    "chatTabText": "#FFAAAAAA",
    "chatTabTextActive": "#FFFFFFFF",
    "chatBadge": "#FFFF5555"
  },

  "geometry": {
    "radiusSmall": 2
  },

	"chat": {
		"width": 0,
		"height": 0,
		"backgroundOpacity": 1.0,
    "tabHeight": 12,
    "tabGap": 2,
    "overrideBackground": true
  }
}
```

Отсутствующие или некорректные поля используют значение встроенной темы; неизвестные поля
игнорируются. Отсутствующий или повреждённый активный файл, как и некорректный `active.txt`,
включает встроенную тему без аварийного завершения клиента. `chat.overrideBackground: true`
включает цвет `colors.chatBackground` для фона строк чата. При `0 <= backgroundOpacity <= 1`
итоговая alpha равна `alpha(chatBackground) × backgroundOpacity`; RGB всегда берётся из
`chatBackground`. Поэтому темы с заменой фона задают `backgroundOpacity: 1.0` и собственную alpha
цвета. При `overrideBackground: false` сохраняется исходный vanilla-фон вместе с настройкой его
прозрачности и затуханием сообщений; это поведение встроенных тем `default` и `vanilla`.
`chat.padding` задаёт отступ текста и горизонтальные границы фона, hit-testing и выделения от
`0` до `32` px. `chat.messageSpacing` задаёт дополнительный шаг строк от `0` до `32` px и
одинаково применяется к layout, hit-testing и выделению. Значения вне диапазонов ограничиваются
ближайшей границей. Встроенные значения `padding: 4` и `messageSpacing: 1` сохраняют vanilla layout.
`geometry.borderWidth` влияет на рамки панелей, кнопок и полей ввода. `geometry.spacing*`
применяется на owo-экранах и owo-компонентах. `geometry.radius*` пока применяется только к
owo-компонентам; vanilla-отрисовка скруглений не поддерживает.
`chat.width` и `chat.height` задают размер чата в пикселях до scale — тех же единицах, что
возвращают `ChatComponent.getWidth()` и `getHeight()`. Значение `0` или меньше сохраняет
vanilla размер. Кнопка «Открыть папку» на экране тем открывает каталог тем в файловом менеджере ОС.

## Совместимое представление и automation bridge

`cndl-chat-plus.json` сохраняет глобальные переключатели чата, истории, закладок, HUD, звуков,
Chat Alerts, бинды, `customChatTabs`, `hiddenBuiltInTabs` и совместимое видимое представление
Vanilla-box. Сохранение использует atomic temporary file и move.

`chatTabTextScalePercent` задаёт общий размер текста вкладок над чатом от `50` до `200` процентов.
Отсутствующее или некорректное значение означает `100`. Legacy `chatTabTextSize` со значениями
 `SMALL`, `NORMAL` или `LARGE` остаётся migration bridge и при отсутствии процента даёт 80, 100 или 120.

`clanLookupEnabled` — глобальный переключатель запросов `/clan lookup`; отсутствующее или `null`
значение означает `true` для совместимости. Он не меняет command template в fixed Vanilla-box config.

Каждый бинд содержит основную клавишу и необязательные Ctrl, Shift и Alt. Отсутствующие в старом
JSON поля модификаторов означают обычный одиночный бинд.

`customChatTabs` является глобальным списком пользовательских вкладок с полями `id`, `name`,
`sources` и необязательным `outgoingPrefix`. `sources` содержит один или несколько фиксированных
типов: `GLOBAL`, `LOCAL`, `CLAN`, `PRIVATE`, `DISCORD`, `SYSTEM`, `VOICE`. Произвольного текстового
фильтра нет. При отсутствии поля или JSON `null` создаётся одна вкладка по умолчанию: «ГС чат»,
источник `VOICE`, префикс `/gc`. Явный пустой список означает, что пользовательских вкладок нет.
Каждая корректная вкладка должна иметь хотя бы один источник. Повреждённые или не являющиеся
объектами элементы пропускаются, пустые списки источников отбрасываются, дубликаты источников
удаляются, пустому или повторному ID назначается новый ID. Legacy-вкладка с маркером `(Войс)`
однократно читается как `VOICE`; другие произвольные маркеры не исполняются. Список ограничен
32 вкладками, ID и название имеют лимит 64 символа, префикс — 128 символов.

`hiddenBuiltInTabs` является глобальным списком имён скрытых встроенных вкладок. Явный пустой
список показывает все встроенные вкладки. Некорректные, пустые и повторные имена игнорируются.
Оба поля добавляются к основному JSON, не входят в `VanillaBoxConfig`, не участвуют в migration
и не меняют fixed Vanilla-box config.

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
атомарно после явного действия; автоматически старые закладки не удаляются. Если bookmark JSON
нельзя прочитать полностью, перед следующим сохранением исходный файл переносится рядом под
именем `*.unreadable-<uuid>.bak`, поэтому его байты не перезаписываются. Повреждённый
history/bookmark JSON не завершает клиент аварийно, но соответствующий runtime list будет пустым.

Глобальные Chat Alerts сопоставляют только видимые после фильтрации сообщения. Их регулярные
выражения компилируются при сохранении, а не в message hot path. Private messages, email,
reply payloads и суммы не должны попадать в логи.

## Границы UI

Нет UI для создания, копирования, выбора, default, binding, resolve, catalog или import server
configurations. В настройках чата есть только редактор глобальных пользовательских вкладок и
видимости встроенных вкладок. Нет editable UI для fixed server commands или parser settings.
Фабрики Java не являются пользовательской конфигурацией и не перезаписывают существующие данные.
