package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends CompatScreen {
	private final Screen parent;
	private final ResponderConfig config;
	private final ScreenStatus status = new ScreenStatus();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private boolean narrow;
	private Page page = Page.CHAT;

	public SettingsScreen(Screen parent, ResponderConfig config) {
		super(Component.literal("Настройки CNDL_chat+"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(680, width - 24);
		panelHeight = Math.min(250, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		narrow = panelWidth < 520;
		int gap = 16;
		int columnWidth = (panelWidth - 52 - gap) / 2;
		int left = panelX + 18;
		int right = left + columnWidth + gap;
		int firstY = panelY + (narrow ? 64 : 52);
		int rowGap = 25;

		if (narrow) {
			int pageWidth = (panelWidth - 36) / 3;
			addPageButton(Page.CHAT, left, panelY + 34, pageWidth);
			addPageButton(Page.DISPLAY, left + pageWidth, panelY + 34,
					pageWidth);
			addPageButton(Page.SERVER, left + pageWidth * 2, panelY + 34,
					panelWidth - 36 - pageWidth * 2);
			if (page == Page.CHAT) {
				addChatSettings(left, panelWidth - 36, firstY, rowGap);
			} else if (page == Page.DISPLAY) {
				addDisplaySettings(left, panelWidth - 36, firstY, rowGap);
			} else {
				addServerSelector(left, firstY, panelWidth - 36);
				addServerSettingsButton(left, firstY + rowGap, panelWidth - 36);
			}
		} else {
			addChatSettings(left, columnWidth, firstY, rowGap);
			addDisplaySettings(right, columnWidth, firstY, rowGap);
		}

		int buttonY = panelY + panelHeight - 30;
		int backWidth = narrow ? 50 : 80;
		if (!narrow) {
			int serverButtonWidth = Math.min(260, panelWidth - 132);
			addServerSettingsButton(left, buttonY, serverButtonWidth);
			int selectorX = left + serverButtonWidth + 8;
			int backX = panelX + panelWidth - 18 - backWidth;
			addServerSelector(selectorX, buttonY, backX - selectorX - 8);
		}
		addRenderableWidget(StyledButton.create(Component.literal("Назад"), ignored -> onClose())
				.bounds(panelX + panelWidth - 18 - backWidth, buttonY, backWidth, FIELD_HEIGHT).build());
	}

	private void addServerSettingsButton(int x, int y, int buttonWidth) {
		addRenderableWidget(StyledButton.create(Component.literal("Настройка команд для сервера"), ignored ->
				ClientUi.setScreen(minecraft, new TemplatesScreen(this)))
				.bounds(x, y, buttonWidth, FIELD_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Открыть серверные шаблоны, команды и форматы")))
				.build());
	}

	private void addServerSelector(int x, int y, int buttonWidth) {
		TemplateOperationResult<RootConfig> loaded = ConfigManager.templateRepository().loadRoot();
		String activeId = CndlChatPlusClient.TEMPLATE_RUNTIME == null ? null
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::id).orElse(null);
		if (!loaded.success() || loaded.value().templates.isEmpty()) return;
		List<String> ids = loaded.value().templates.stream().map(info -> info.id).toList();
		String initial = activeId != null && ids.contains(activeId) ? activeId : ids.getFirst();
		StyledCycleButton<String> selector = StyledCycleButton.of(
				id -> Component.literal(templateName(loaded.value(), id)), initial, ids,
				x, y, buttonWidth, FIELD_HEIGHT, Component.literal("Сервер"),
				(button, id) -> selectTemplate(id));
		addRenderableWidget(selector);
		selector.setTooltip(Tooltip.create(Component.literal("Выбрать активные команды и форматы сервера")));
	}

	private static String templateName(RootConfig root, String id) {
		return root.templates.stream().filter(info -> id.equals(info.id))
				.map(info -> info.name).findFirst().orElse(id);
	}

	private void selectTemplate(String id) {
		String current = CndlChatPlusClient.TEMPLATE_RUNTIME == null ? null
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::id).orElse(null);
		if (id.equals(current)) return;
		TemplateOperationResult<ServerTemplate> selected = CndlChatPlusClient.TEMPLATE_SELECTION.select(id);
		if (!selected.success()) {
			status.set(selected.errorMessage(), ERROR);
			rebuild();
			return;
		}
		if (parent instanceof ResponderScreen responder) responder.activeTemplateChanged();
		status.set("Активный сервер: " + selected.value().name, SUCCESS);
		rebuild();
	}

	private void addPageButton(Page target, int x, int y, int buttonWidth) {
		Button button = addRenderableWidget(StyledButton.create(Component.literal(target.title), ignored -> {
			page = target;
			rebuild();
		}).bounds(x, y, buttonWidth, FIELD_HEIGHT).build());
		button.active = page != target;
	}

	private void addChatSettings(int x, int buttonWidth, int firstY, int rowGap) {
		addToggle("Вкладки чата", Boolean.TRUE.equals(config.chatTabsEnabled), x, firstY,
				buttonWidth, "Показывать группы каналов над чатом", value -> {
					config.chatTabsEnabled = value;
					if (!value && CndlChatPlusClient.CHAT_TABS != null) {
						CndlChatPlusClient.CHAT_TABS.resetRuntimeState();
					}
					if (CndlChatPlusClient.CHAT_TABS != null) {
						CndlChatPlusClient.CHAT_TABS.refresh(minecraft);
					}
				});
		addToggle("Поиск по чату", Boolean.TRUE.equals(config.chatSearchEnabled), x, firstY + rowGap,
				buttonWidth, "Включить поиск по Ctrl+F", value -> {
					config.chatSearchEnabled = value;
					if (!value && CndlChatPlusClient.CHAT_SEARCH != null) CndlChatPlusClient.CHAT_SEARCH.clear();
				});
		addToggle("Время сообщений", Boolean.TRUE.equals(config.chatTimestampsEnabled), x,
				firstY + rowGap * 2, buttonWidth, "Добавлять префикс [HH:mm]", value -> {
					config.chatTimestampsEnabled = value;
					if (!value && CndlChatPlusClient.CHAT_TIMESTAMPS != null) {
						CndlChatPlusClient.CHAT_TIMESTAMPS.resetRuntimeState();
					}
				});
		addToggle("Объединять повторы", Boolean.TRUE.equals(config.chatDuplicateCollapseEnabled), x,
				firstY + rowGap * 3, buttonWidth, "Собирать одинаковые сообщения в строку xN", value -> {
					config.chatDuplicateCollapseEnabled = value;
					if (!value && CndlChatPlusClient.CHAT_DUPLICATES != null) {
						CndlChatPlusClient.CHAT_DUPLICATES.reset();
					}
				});
		addToggle("Меню сообщений по ПКМ", Boolean.TRUE.equals(config.chatContextMenuEnabled), x,
				firstY + rowGap * 4, buttonWidth, "Открывать действия по ПКМ на сообщении",
				value -> config.chatContextMenuEnabled = value);
	}

	private void addDisplaySettings(int x, int buttonWidth, int firstY, int rowGap) {
		addToggle("Чат Discord", Boolean.TRUE.equals(config.discordChatEnabled), x, firstY,
				buttonWidth, "Показывать сообщения Discord-моста", value -> config.discordChatEnabled = value);
		addToggle("HUD друзей", Boolean.TRUE.equals(config.friendHudEnabled), x, firstY + rowGap,
				buttonWidth, "Показывать онлайн-друзей справа снизу", value -> config.friendHudEnabled = value);
		addToggle("Звук появления друга", Boolean.TRUE.equals(config.friendSoundEnabled), x,
				firstY + rowGap * 2, buttonWidth, "Проигрывать звук, когда друг входит",
				value -> config.friendSoundEnabled = value);
		addToggle("Звук входящего ТП", Boolean.TRUE.equals(config.teleportRequestSoundEnabled), x,
				firstY + rowGap * 3, buttonWidth, "Проигрывать звук входящего запроса телепорта",
				value -> config.teleportRequestSoundEnabled = value);
	}

	private void addToggle(String name, boolean initial, int x, int y, int buttonWidth, String help,
			Consumer<Boolean> update) {
		StyledCycleButton<Boolean> toggle = StyledCycleButton.onOff(initial, x, y, buttonWidth, FIELD_HEIGHT,
				Component.literal(name), (button, value) -> {
					update.accept(value);
					boolean saved = ConfigManager.saveGlobalSettings(config);
					status.set(saved ? "Настройки сохранены" : "Не удалось сохранить настройки",
							saved ? SUCCESS : ERROR);
				});
		addRenderableWidget(toggle);
		toggle.setTooltip(Tooltip.create(Component.literal(help)));
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 14);
		if (!narrow) {
			graphics.text(font, "Чат", panelX + 18, panelY + 38, ACCENT_SOFT);
			graphics.text(font, "Отображение и звуки", panelX + panelWidth / 2 + 8, panelY + 38, ACCENT_SOFT);
		}
		if (!status.empty() && (!narrow || panelHeight >= 240)) {
			graphics.centeredText(font, status.text(), width / 2, panelY + panelHeight - 44, status.color());
		}
	}

	@Override
	public void onClose() {
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	private enum Page {
		CHAT("Чат"),
		DISPLAY("HUD и звуки"),
		SERVER("Сервер");

		private final String title;

		Page(String title) {
			this.title = title;
		}
	}
}
