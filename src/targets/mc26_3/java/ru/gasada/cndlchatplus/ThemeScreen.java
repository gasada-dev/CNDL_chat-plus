package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.ThemeTokens.*;

import java.nio.file.Path;
import java.util.List;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ThemeScreen extends CompatScreen {
	private static final int PANEL_WIDTH = 430;
	private static final int PANEL_HEIGHT = 250;
	private static final int NARROW_WIDTH = 400;
	private final Screen parent;
	private List<UiTheme.Meta> themes = List.of();
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int page;
	private String summary = "";

	ThemeScreen(Screen parent) {
		super(Component.literal("Темы"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(PANEL_WIDTH, width - 24);
		panelHeight = Math.min(PANEL_HEIGHT, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		themes = themes();
		summary = fit("Найдено: " + ThemeManager.available().size() + " • "
				+ ThemeManager.themesDirectory(), "", panelWidth - 36);
		boolean narrow = panelWidth < NARROW_WIDTH;
		int listTop = panelY + 62;
		int controlsY = panelY + panelHeight - (narrow ? 54 : 30);
		int pageSize = Math.max(1, (controlsY - listTop - 6) / ROW_HEIGHT);
		page = Pagination.clampPage(page, themes.size(), pageSize);
		int start = page * pageSize;
		String activeId = ThemeManager.current().meta().id();
		for (int row = 0; row < pageSize && start + row < themes.size(); row++) {
			UiTheme.Meta theme = themes.get(start + row);
			boolean active = theme.id().equals(activeId);
			String label = theme.name() + " — " + theme.author();
			String marker = active ? "  ✔" : "";
			String fullLabel = label + marker;
			Button button = addRenderableWidget(StyledButton.create(
					Component.literal(fit(label, marker, panelWidth - 44)), ignored -> apply(theme.id()))
					.bounds(panelX + 18, listTop + row * ROW_HEIGHT, panelWidth - 36, FIELD_HEIGHT)
					.tooltip(Tooltip.create(Component.literal(fullLabel))).build());
			button.active = !active;
		}

		if (narrow) {
			addActionButtons(panelX + 18, controlsY, panelWidth - 36);
			addPageButtons(panelX + 18, controlsY + 24, pageSize);
			addBackButton(controlsY + 24);
		} else {
			addPageButtons(panelX + 18, controlsY, pageSize);
			addActionButtons(panelX + 86, controlsY, panelWidth - 186);
			addBackButton(controlsY);
		}
	}

	private void addPageButtons(int x, int y, int pageSize) {
		Button previous = addRenderableWidget(StyledButton.create(Component.literal("<"), ignored -> {
			page--;
			rebuild();
		}).bounds(x, y, 28, FIELD_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Предыдущая страница"))).build());
		previous.active = page > 0;
		Button next = addRenderableWidget(StyledButton.create(Component.literal(">"), ignored -> {
			page++;
			rebuild();
		}).bounds(x + 32, y, 28, FIELD_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Следующая страница"))).build());
		next.active = page < Pagination.maxPage(themes.size(), pageSize);
	}

	private void addActionButtons(int x, int y, int width) {
		int reloadWidth = (width - 4) / 2;
		String reloadLabel = reloadWidth < 108 ? "Обновить" : "Обновить список";
		String folderLabel = width - reloadWidth - 4 < 100 ? "Папка" : "Открыть папку";
		addRenderableWidget(StyledButton.create(Component.literal(reloadLabel), ignored -> {
			ThemeManager.reload();
			rebuild();
		}).bounds(x, y, reloadWidth, FIELD_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Обновить список тем"))).build());
		addRenderableWidget(StyledButton.create(Component.literal(folderLabel), ignored -> openDirectory())
				.bounds(x + reloadWidth + 4, y, width - reloadWidth - 4, FIELD_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Открыть папку тем"))).build());
	}

	private void addBackButton(int y) {
		addRenderableWidget(StyledButton.create(Component.literal("Назад"), ignored -> onClose())
				.bounds(panelX + panelWidth - 92, y, 74, FIELD_HEIGHT).build());
	}

	private static List<UiTheme.Meta> themes() {
		List<UiTheme.Meta> themes = ThemeManager.available();
		return themes.isEmpty() ? List.of(UiTheme.defaults().meta()) : themes;
	}

	private String fit(String text, String suffix, int maxWidth) {
		if (font.width(text + suffix) <= maxWidth) return text + suffix;
		String ending = "..." + suffix;
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ending))) + ending;
	}

	private void apply(String id) {
		if (!ThemeManager.setTheme(id)) return;
		ChatAccess.chat(minecraft).rescaleChat();
		rebuild();
	}

	private void openDirectory() {
		Path directory = ThemeManager.themesDirectory();
		try {
			Blaze3D.openPath(directory);
		} catch (RuntimeException error) {
			CndlChatPlusClient.LOGGER.warn("Не удалось открыть папку тем {}", directory, error);
		}
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
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 14);
		graphics.centeredText(font, summary, width / 2, panelY + 45, textMuted());
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
