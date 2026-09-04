package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChatAlertsScreen extends CompatScreen {
	private final Screen parent;
	private final ResponderConfig config;
	private final ScreenStatus status = new ScreenStatus();
	private String selectedId;
	private int page;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public ChatAlertsScreen(Screen parent, ResponderConfig config) {
		super(Component.literal("Chat Alerts"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(700, width - 24);
		panelHeight = Math.min(330, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int left = panelX + 16;
		int usable = panelWidth - 32;
		addRenderableWidget(StyledCycleButton.onOff(Boolean.TRUE.equals(config.chatAlertsEnabled),
				left, panelY + 34, Math.min(220, usable), FIELD_HEIGHT, Component.literal("Chat Alerts"),
				(button, value) -> {
					config.chatAlertsEnabled = value;
					persist();
				}));

		int pageSize = pageSize();
		page = Pagination.clampPage(page, config.chatAlertRules.size(), pageSize);
		int start = page * pageSize;
		for (int row = 0; row < pageSize && start + row < config.chatAlertRules.size(); row++) {
			ChatAlertRule rule = config.chatAlertRules.get(start + row);
			String label = (rule.enabled ? "[x] " : "[ ] ")
					+ rule.matchType.displayName() + ": " + compact(rule.pattern, 56) + " | "
					+ rule.channel.displayName() + " | " + actions(rule);
			Button button = addRenderableWidget(StyledButton.create(Component.literal(label), ignored -> {
				selectedId = rule.id;
				rebuild();
			}).bounds(left, panelY + 62 + row * 24, usable, FIELD_HEIGHT).build());
			button.active = !rule.id.equals(selectedId);
		}

		boolean narrow = panelWidth < 520;
		int controlsY = panelY + panelHeight - (narrow ? 52 : 54);
		Button previous = addRenderableWidget(StyledButton.create(Component.literal("<"), ignored -> {
			page--;
			rebuild();
		}).bounds(left, controlsY, 28, FIELD_HEIGHT).build());
		previous.active = page > 0;
		Button next = addRenderableWidget(StyledButton.create(Component.literal(">"), ignored -> {
			page++;
			rebuild();
		}).bounds(left + 32, controlsY, 28, FIELD_HEIGHT).build());
		next.active = page < Pagination.maxPage(config.chatAlertRules.size(), pageSize);

		addRenderableWidget(StyledButton.create(Component.literal("Добавить"), ignored -> addRule())
				.bounds(left + 72, controlsY, narrow ? 70 : 86, FIELD_HEIGHT).build());
		Button edit = addRenderableWidget(StyledButton.create(Component.literal("Изменить"), ignored -> editRule())
				.bounds(left + (narrow ? 146 : 162), controlsY, narrow ? 70 : 86, FIELD_HEIGHT).build());
		Button toggle = addRenderableWidget(StyledButton.create(Component.literal("Вкл/выкл"), ignored -> toggleRule())
				.bounds(narrow ? left : left + 252, narrow ? controlsY + 24 : controlsY,
						narrow ? 72 : 86, FIELD_HEIGHT).build());
		Button delete = addRenderableWidget(StyledButton.create(Component.literal("Удалить"), ignored -> deleteRule())
				.bounds(narrow ? left + 76 : left + 342, narrow ? controlsY + 24 : controlsY,
						narrow ? 68 : 78, FIELD_HEIGHT).build());
		edit.active = selected() != null;
		toggle.active = selected() != null;
		delete.active = selected() != null;
		addRenderableWidget(StyledButton.create(Component.literal("Готово"), ignored -> onClose())
				.bounds(panelX + panelWidth - 96, narrow ? controlsY + 24 : controlsY, 80, FIELD_HEIGHT).build());
	}

	private int pageSize() {
		return Math.max(2, Math.min(8, (panelHeight - (panelWidth < 520 ? 154 : 130)) / 24));
	}

	private void addRule() {
		if (config.chatAlertRules.size() >= ResponderConfig.MAX_CHAT_ALERT_RULES) {
			status.set("Достигнут лимит 100 правил", ERROR);
			return;
		}
		ClientUi.setScreen(minecraft, new ChatAlertEditScreen(this, null, this::saveRule));
	}

	private void editRule() {
		ChatAlertRule selected = selected();
		if (selected != null) ClientUi.setScreen(minecraft,
				new ChatAlertEditScreen(this, selected, this::saveRule));
	}

	private void saveRule(ChatAlertRule rule) {
		int index = indexOf(rule.id);
		if (index < 0) config.chatAlertRules.add(rule);
		else config.chatAlertRules.set(index, rule);
		selectedId = rule.id;
		persist();
	}

	private void toggleRule() {
		ChatAlertRule selected = selected();
		if (selected == null) return;
		selected.enabled = !selected.enabled;
		persist();
		rebuild();
	}

	private void deleteRule() {
		int index = indexOf(selectedId);
		if (index < 0) return;
		config.chatAlertRules.remove(index);
		selectedId = null;
		persist();
		rebuild();
	}

	private void persist() {
		config.sanitize();
		boolean saved = ConfigManager.saveGlobalSettings(config);
		if (saved && CndlChatPlusClient.CHAT_ALERTS != null) {
			CndlChatPlusClient.CHAT_ALERTS.reload(config.chatAlertRules);
		}
		status.set(saved ? "Alert-правила сохранены" : "Не удалось сохранить alert-правила",
				saved ? SUCCESS : ERROR);
	}

	private ChatAlertRule selected() {
		int index = indexOf(selectedId);
		return index < 0 ? null : config.chatAlertRules.get(index);
	}

	private int indexOf(String id) {
		if (id == null) return -1;
		for (int index = 0; index < config.chatAlertRules.size(); index++) {
			if (id.equals(config.chatAlertRules.get(index).id)) return index;
		}
		return -1;
	}

	private static String compact(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
	}

	private static String actions(ChatAlertRule rule) {
		if (rule.hudEnabled && rule.soundEnabled) return "HUD + звук";
		if (rule.hudEnabled) return "HUD";
		if (rule.soundEnabled) return "звук";
		return "без уведомления";
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
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 12);
		if (config.chatAlertRules.isEmpty()) {
			graphics.centeredText(font, "Alert-правил пока нет", width / 2, panelY + 92, MUTED);
		}
		if (!status.empty()) graphics.centeredText(font, status.text(), width / 2,
				panelY + panelHeight - 76, status.color());
	}

	@Override
	public void onClose() {
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
