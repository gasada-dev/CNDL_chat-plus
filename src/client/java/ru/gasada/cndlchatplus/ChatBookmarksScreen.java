package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class ChatBookmarksScreen extends CompatScreen {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm");
	private final Screen parent;
	private final ChatBookmarkStore store;
	private String selectedId;
	private int page;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private String status = "";

	public ChatBookmarksScreen(Screen parent, ChatBookmarkStore store) {
		super(Component.literal("Закладки сообщений"));
		this.parent = parent;
		this.store = store;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(720, width - 24);
		panelHeight = Math.min(340, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		List<ChatBookmark> bookmarks = store.snapshot();
		int pageSize = Math.max(2, Math.min(6, (panelHeight - 178) / 24));
		page = Pagination.clampPage(page, bookmarks.size(), pageSize);
		int start = page * pageSize;
		for (int row = 0; row < pageSize && start + row < bookmarks.size(); row++) {
			ChatBookmark bookmark = bookmarks.get(start + row);
			Button button = addRenderableWidget(StyledButton.create(Component.literal(rowLabel(bookmark)), ignored -> {
				selectedId = bookmark.id();
				rebuild();
			}).bounds(panelX + 16, panelY + 58 + row * 24, panelWidth - 32, FIELD_HEIGHT).build());
			button.active = !bookmark.id().equals(selectedId);
		}

		boolean narrow = panelWidth < 520;
		int controlsY = panelY + panelHeight - (narrow ? 52 : 30);
		Button previous = addRenderableWidget(StyledButton.create(Component.literal("<"), ignored -> {
			page--;
			rebuild();
		}).bounds(panelX + 16, controlsY, 28, FIELD_HEIGHT).build());
		previous.active = page > 0;
		Button next = addRenderableWidget(StyledButton.create(Component.literal(">"), ignored -> {
			page++;
			rebuild();
		}).bounds(panelX + 48, controlsY, 28, FIELD_HEIGHT).build());
		next.active = page < Pagination.maxPage(bookmarks.size(), pageSize);
		Button copy = addRenderableWidget(StyledButton.create(Component.literal("Копировать"), ignored -> copy())
				.bounds(panelX + 88, controlsY, narrow ? 86 : 98, FIELD_HEIGHT).build());
		Button delete = addRenderableWidget(StyledButton.create(Component.literal("Удалить"), ignored -> delete())
				.bounds(panelX + (narrow ? 178 : 190), controlsY, narrow ? 70 : 82, FIELD_HEIGHT).build());
		Button clear = addRenderableWidget(StyledButton.create(Component.literal("Очистить все"), ignored ->
				ClientUi.setScreen(minecraft, new ChatBookmarksClearScreen(this, store, this::cleared)))
				.bounds(narrow ? panelX + 16 : panelX + 280, narrow ? controlsY + 24 : controlsY,
						narrow ? 104 : 104, FIELD_HEIGHT).build());
		copy.active = selected() != null;
		delete.active = selected() != null;
		clear.active = !bookmarks.isEmpty();
		addRenderableWidget(StyledButton.create(Component.literal("Готово"), ignored -> onClose())
				.bounds(panelX + panelWidth - 96, narrow ? controlsY + 24 : controlsY, 80, FIELD_HEIGHT).build());
	}

	private String rowLabel(ChatBookmark bookmark) {
		String date = DATE.format(Instant.ofEpochMilli(bookmark.savedAtMillis()).atZone(ZoneId.systemDefault()));
		String channel = channelName(bookmark.channel());
		String sender = bookmark.sender() == null ? "" : " " + bookmark.sender();
		String prefix = "[" + date + "] [" + channel + "]" + sender + " | ";
		String text = bookmark.text().replace('\n', ' ');
		String full = prefix + text;
		int availableWidth = panelWidth - 44;
		if (font.width(full) <= availableWidth) return full;
		return font.plainSubstrByWidth(full, availableWidth - font.width("...")) + "...";
	}

	private static String channelName(String value) {
		try {
			return ChatTab.valueOf(value).displayName();
		} catch (IllegalArgumentException exception) {
			return "Система";
		}
	}

	private void copy() {
		ChatBookmark bookmark = selected();
		if (bookmark != null) minecraft.keyboardHandler.setClipboard(bookmark.text());
	}

	private void delete() {
		if (selected() == null) return;
		store.remove(selectedId);
		status = store.lastSaveSucceeded() ? "Закладка удалена" : "Не удалось сохранить удаление";
		selectedId = null;
		rebuild();
	}

	private void cleared() {
		selectedId = null;
		page = 0;
		status = store.lastSaveSucceeded() ? "Все закладки удалены" : "Не удалось сохранить очистку";
	}

	private ChatBookmark selected() {
		if (selectedId == null) return null;
		return store.snapshot().stream().filter(bookmark -> selectedId.equals(bookmark.id())).findFirst().orElse(null);
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
		graphics.centeredText(font, "Сервер: " + store.scopeLabel(), width / 2, panelY + 36, MUTED);
		ChatBookmark selected = selected();
		if (selected != null) {
			List<FormattedCharSequence> lines = font.split(Component.literal(selected.text()), panelWidth - 36);
			int previewY = panelY + panelHeight - 92;
			for (int index = 0; index < Math.min(3, lines.size()); index++) {
				graphics.text(font, lines.get(index), panelX + 18, previewY + index * 10, TEXT);
			}
		} else if (store.snapshot().isEmpty()) {
			graphics.centeredText(font, "Закладок пока нет", width / 2, panelY + 92, MUTED);
		}
		if (!status.isEmpty()) graphics.centeredText(font, status, width / 2,
				panelY + panelHeight - (panelWidth < 520 ? 68 : 46),
				store.lastSaveSucceeded() ? SUCCESS : ERROR);
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
