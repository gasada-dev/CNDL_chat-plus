package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class UpdateAvailableScreen extends CompatScreen {
	private static final int PANEL_WIDTH = 360;
	private static final int PANEL_HEIGHT = 180;
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
		addRenderableWidget(StyledButton.create(Component.literal("Скачать 1.21.11"), ignored ->
				ConfirmLinkScreen.confirmLinkNow(this, update.minecraft12111DownloadUrl()))
				.bounds(panelX + 24, panelY + 112, 150, 20)
				.tooltip(Tooltip.create(Component.literal("Открыть JAR для Minecraft 1.21.11 на GitHub")))
				.build());
		addRenderableWidget(StyledButton.create(Component.literal("Скачать 26.2"), ignored ->
				ConfirmLinkScreen.confirmLinkNow(this, update.minecraft262DownloadUrl()))
				.bounds(panelX + 186, panelY + 112, 150, 20)
				.tooltip(Tooltip.create(Component.literal("Открыть JAR для Minecraft 26.2 на GitHub")))
				.build());
		addRenderableWidget(StyledButton.create(Component.literal("Позже"), ignored -> onClose())
				.bounds(panelX + 105, panelY + 142, 150, 20)
				.tooltip(Tooltip.create(Component.literal("Закрыть уведомление до следующего запуска")))
				.build());
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xD010141D);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		int panelX = (width - PANEL_WIDTH) / 2;
		int panelY = (height - PANEL_HEIGHT) / 2;
		ScreenChrome.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 16);
		graphics.centeredText(font, "Установлена: " + currentVersion + "  →  Новая: " + update.version(),
				width / 2, panelY + 42, ACCENT_SOFT);
		String message = update.message() == null || update.message().isBlank()
				? "Рекомендуется установить актуальную версию мода."
				: update.message();
		graphics.textWithWordWrap(font, Component.literal(message), panelX + 24, panelY + 62,
				PANEL_WIDTH - 48, 0xFFCED5E0);
		CreditRenderer.draw(graphics, font, panelX + 6, panelY + PANEL_HEIGHT - 12, MUTED);
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
