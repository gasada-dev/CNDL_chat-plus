package ru.gasada.chatresponder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UpdateAvailableScreen extends Screen {
	private static final int PANEL_WIDTH = 360;
	private static final int PANEL_HEIGHT = 150;
	private final Screen parent;
	private final String currentVersion;
	private final UpdateChecker.UpdateInfo update;

	public UpdateAvailableScreen(Screen parent, String currentVersion, UpdateChecker.UpdateInfo update) {
		super(Component.literal("Доступно обновление CNDL_chat+"));
		this.parent = parent;
		this.currentVersion = currentVersion;
		this.update = update;
	}

	@Override
	protected void init() {
		int panelX = (width - PANEL_WIDTH) / 2;
		int panelY = (height - PANEL_HEIGHT) / 2;
		addRenderableWidget(Button.builder(Component.literal("Скачать " + update.version), ignored ->
				ConfirmLinkScreen.confirmLinkNow(this, update.downloadUrl))
				.bounds(panelX + 24, panelY + 105, 150, 20)
				.tooltip(Tooltip.create(Component.literal("Открыть полный JAR-файл на GitHub")))
				.build());
		addRenderableWidget(Button.builder(Component.literal("Позже"), ignored -> onClose())
				.bounds(panelX + 186, panelY + 105, 150, 20)
				.tooltip(Tooltip.create(Component.literal("Закрыть уведомление до следующего запуска")))
				.build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xD010141D);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int panelX = (width - PANEL_WIDTH) / 2;
		int panelY = (height - PANEL_HEIGHT) / 2;
		graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2,
				0xFF536178);
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF0242B38);
		graphics.centeredText(font, title, width / 2, panelY + 16, 0xFFE8ECF2);
		graphics.centeredText(font, "Установлена: " + currentVersion + "  →  Новая: " + update.version,
				width / 2, panelY + 42, 0xFFB76EF5);
		String message = update.message == null || update.message.isBlank()
				? "Рекомендуется установить актуальную версию мода."
				: update.message;
		graphics.textWithWordWrap(font, Component.literal(message), panelX + 24, panelY + 62,
				PANEL_WIDTH - 48, 0xFFCED5E0);
		CreditRenderer.draw(graphics, font, panelX + 6, panelY + PANEL_HEIGHT - 12, 0xFF9DA8B8);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
