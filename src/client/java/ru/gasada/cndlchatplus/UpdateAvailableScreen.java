package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.List;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class UpdateAvailableScreen extends CompatScreen {
	private static final int MAX_PANEL_WIDTH = 520;
	private static final int MAX_PANEL_HEIGHT = 300;
	private static final String DEFAULT_NOTES = "Рекомендуется установить актуальную версию мода.";
	private final Screen parent;
	private final String currentVersion;
	private final UpdateChecker.UpdateInfo update;
	private final Component notes;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int notesY;
	private int maxNotesLines;
	private List<FormattedCharSequence> noteLines = List.of();

	public UpdateAvailableScreen(Screen parent, String currentVersion, UpdateChecker.UpdateInfo update) {
		super(Component.literal("Доступно обновление CNDL_chat+"));
		this.parent = parent;
		this.currentVersion = currentVersion;
		this.update = update;
		this.notes = Component.literal(formatNotes(update.message()));
	}

	@Override
	protected void init() {
		panelWidth = Math.min(MAX_PANEL_WIDTH, width - 24);
		panelHeight = Math.min(MAX_PANEL_HEIGHT, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int laterY = panelY + panelHeight - 38;
		int downloadY = laterY - 30;
		int changesY = downloadY - 30;
		int downloadWidth = (panelWidth - 60) / 2;
		notesY = panelY + 62;
		maxNotesLines = Math.max(1, (changesY - notesY - 8) / font.lineHeight);
		noteLines = font.split(notes, panelWidth - 48);

		addRenderableWidget(StyledButton.create(Component.literal("Посмотреть все изменения"), ignored ->
				ConfirmLinkScreen.confirmLinkNow(this, UpdateChecker.releaseNotesUrl(update.version())))
				.bounds(panelX + (panelWidth - 200) / 2, changesY, 200, 20)
				.tooltip(Tooltip.create(Component.literal("Открыть UPDATE_NOTES.md этой версии на GitHub")))
				.build());
		addRenderableWidget(StyledButton.create(Component.literal("Скачать 1.21.11"), ignored ->
				ConfirmLinkScreen.confirmLinkNow(this, update.minecraft12111DownloadUrl()))
				.bounds(panelX + 24, downloadY, downloadWidth, 20)
				.tooltip(Tooltip.create(Component.literal("Открыть JAR для Minecraft 1.21.11 на GitHub")))
				.build());
		addRenderableWidget(StyledButton.create(Component.literal("Скачать 26.2"), ignored ->
				ConfirmLinkScreen.confirmLinkNow(this, update.minecraft262DownloadUrl()))
				.bounds(panelX + 36 + downloadWidth, downloadY, downloadWidth, 20)
				.tooltip(Tooltip.create(Component.literal("Открыть JAR для Minecraft 26.2 на GitHub")))
				.build());
		addRenderableWidget(StyledButton.create(Component.literal("Позже"), ignored -> onClose())
				.bounds(panelX + (panelWidth - 150) / 2, laterY, 150, 20)
				.tooltip(Tooltip.create(Component.literal("Закрыть уведомление до следующего запуска")))
				.build());
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xD010141D);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 16);
		graphics.centeredText(font, "Установлена: " + currentVersion + "  →  Новая: " + update.version(),
				width / 2, panelY + 42, ACCENT_SOFT);
		boolean truncated = noteLines.size() > maxNotesLines;
		int visibleLines = truncated ? Math.max(0, maxNotesLines - 1) : noteLines.size();
		for (int index = 0; index < visibleLines; index++) {
			graphics.text(font, noteLines.get(index), panelX + 24,
					notesY + index * font.lineHeight, 0xFFCED5E0);
		}
		if (truncated) {
			graphics.text(font, "...", panelX + 24,
					notesY + visibleLines * font.lineHeight, 0xFFCED5E0);
		}
		CreditRenderer.draw(graphics, font, panelX + 6, panelY + panelHeight - 12, MUTED);
	}

	static String formatNotes(String message) {
		if (message == null || message.isBlank()) return DEFAULT_NOTES;
		return message.lines()
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.map(line -> line.startsWith("# ") ? line.substring(2) : line)
				.map(line -> line.startsWith("- ") ? "• " + line.substring(2) : line)
				.reduce((first, second) -> first + "\n" + second)
				.orElse(DEFAULT_NOTES);
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
