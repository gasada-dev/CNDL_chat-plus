package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class HelpScreen extends CompatScreen {
	private static final List<Page> PAGES = List.of(
			new Page("Управление и чат", List.of(
					"F8 - открыть менеджер CNDL_chat+.",
					"«Настройки» действуют для всех серверов; экран не ставит игру на паузу.",
					"История чата хранится в памяти и может сохраняться для каждого сервера.",
					"Время сообщений показывается префиксом [HH:mm].",
					"Одинаковые сообщения подряд объединяются со счётчиком x2, x3 и далее.",
					"Вкладки: все, глобал, локал, клан, ЛС, Discord и система.",
					"У вкладок есть счётчики непрочитанных сообщений.",
					"Ctrl+F открывает поиск по текущей вкладке без учёта регистра.",
					"ПКМ по строке чата открывает действия с сообщением или игроком.",
					"Там доступны копирование, ЛС, друг, игнор, профиль, деньги, ТП и почта.")),
			new Page("Каналы и фильтры", List.of(
				"Маркеры локального, глобального, кланового, личного и Discord-чата настраиваются отдельно для каждого серверного шаблона.",
					"Чат Discord можно глобально скрыть через экран настроек.",
					"Отдельных Discord-пользователей можно добавить в локальный мут.",
					"Чёрный список скрывает слова, фразы и маски со знаком *.",
					"Скрытые сообщения не попадают в историю и счётчики вкладок.",
					"Мут Minecraft-игрока вызывает команду active template.",
					"Команды проверяются перед отправкой и не имеют скрытого fallback.")),
			new Page("Друзья и игроки", List.of(
					"Друзья сохраняются отдельно в каждом серверном шаблоне.",
					"Поле ника предлагает онлайн-игроков; Tab подставляет первую подсказку.",
					"Фоновый lookup обновляет последний вход и скрывает служебный ответ.",
					"HUD и звук появления друзей включаются независимо в настройках.",
					"Для выбранного друга доступны ЛС, деньги, ТП и почта.",
					"Экран информации показывает профиль и серверные lookup-поля.",
					"Vanilla-game использует публичный API с безопасным lookup fallback.",
					"При наличии настройки может выполняться поиск брака по страницам.")),
			new Page("Телепорт и Alt+ПКМ", List.of(
					"Входящий запрос ТП показывает HUD-кнопку на 60 секунд; звук отключается отдельно.",
					"Кнопка нажимается после открытия обычного чата.",
					"Автоприём: выкл, от всех, от друзей или от выбранных друзей.",
					"Автопринятый запрос не показывает кнопку и не проигрывает звук.",
					"На Vanilla-box зажмите Alt и нажмите ПКМ по игроку под прицелом.",
					"Меню позволяет добавить игрока в текущий приват: /ps add <ник>.",
					"Удалить из текущего привата: /ps remove <ник>.",
					"Добавить в торговца: /vm trusted add <ник>.",
					"Удалить из торговца: /vm trusted remove <ник>.",
					"Alt+ПКМ работает только в обычной дальности взаимодействия.")),
			new Page("Шаблоны и обновления", List.of(
					"Шаблоны изолируют друзей, фильтры, команды и parsers серверов.",
					"Они открываются через «Настройка команд для сервера» в настройках.",
					"Шаблон выбирается по адресу, binding или значению по умолчанию.",
					"Доступны создание, копирование, переименование и удаление шаблонов.",
					"Команды и regex проверяются перед сохранением и импортом.",
					"Внешние JSON-шаблоны загружаются только вручную из import-папки.",
					"Неизвестный сервер без default template не получает чужие настройки.",
					"Мод проверяет новый GitHub Release после подключения к серверу.",
					"Скачивание открывается только после подтверждения пользователя.")));

	private final Screen parent;
	private int page;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public HelpScreen(Screen parent) {
		super(Component.literal("Подсказка CNDL_chat+"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(720, width - 24);
		panelHeight = Math.max(210, height - 36);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int buttonY = panelY + panelHeight - 28;
		Button previous = addRenderableWidget(StyledButton.create(Component.literal("<"), ignored -> {
			page--;
			rebuild();
		}).bounds(panelX + 16, buttonY, 34, FIELD_HEIGHT).build());
		previous.active = page > 0;
		Button next = addRenderableWidget(StyledButton.create(Component.literal(">"), ignored -> {
			page++;
			rebuild();
		}).bounds(panelX + 56, buttonY, 34, FIELD_HEIGHT).build());
		next.active = page < PAGES.size() - 1;
		addRenderableWidget(StyledButton.create(Component.literal("Назад"), ignored -> onClose())
				.bounds(panelX + panelWidth - 96, buttonY, 80, FIELD_HEIGHT).build());
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
		Page current = PAGES.get(page);
		graphics.centeredText(font, current.title(), width / 2, panelY + 14, ACCENT_SOFT);
		graphics.fill(panelX + 18, panelY + 28, panelX + panelWidth - 18, panelY + 29, BORDER);
		int y = panelY + 42;
		for (String line : current.lines()) {
			graphics.text(font, "•", panelX + 18, y, ACCENT);
			graphics.text(font, line, panelX + 32, y, TEXT);
			y += 14;
		}
		graphics.centeredText(font, (page + 1) + " / " + PAGES.size(), width / 2,
				panelY + panelHeight - 22, MUTED);
	}

	@Override
	public void onClose() {
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record Page(String title, List<String> lines) { }
}
