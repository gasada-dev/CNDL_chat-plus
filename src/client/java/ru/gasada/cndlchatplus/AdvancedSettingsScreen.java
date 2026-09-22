package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AdvancedSettingsScreen extends CompatScreen {
	private final Screen parent;
	private final ResponderConfig config;
	private final ScreenStatus status = new ScreenStatus();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	AdvancedSettingsScreen(Screen parent, ResponderConfig config) {
		super(Component.literal("Расширенные настройки"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(420, width - 24);
		panelHeight = Math.min(180, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int left = panelX + 16;
		int usable = panelWidth - 32;
		StyledCycleButton<Boolean> lookup = StyledCycleButton.onOff(
				Boolean.TRUE.equals(config.clanLookupEnabled), left, panelY + 48, usable, FIELD_HEIGHT,
				Component.literal("Клан-поиск"), (button, value) -> {
					config.clanLookupEnabled = value;
					boolean saved = ConfigManager.saveGlobalSettings(config);
					status.set(saved ? "Настройки сохранены" : "Не удалось сохранить настройки",
							saved ? SUCCESS : ERROR);
				});
		lookup.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
				Component.literal("Разрешить запросы /clan lookup для друзей и профилей")));
		addRenderableWidget(lookup);
		addRenderableWidget(StyledButton.create(Component.literal("Назад"), ignored -> onClose())
				.bounds(panelX + panelWidth - 96, panelY + panelHeight - 30, 80, FIELD_HEIGHT).build());
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 14);
		graphics.text(font, "Настройка запросов к серверу", panelX + 16, panelY + 34, ACCENT_SOFT);
		if (!status.empty()) {
			graphics.centeredText(font, status.text(), width / 2, panelY + panelHeight - 52, status.color());
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
}
