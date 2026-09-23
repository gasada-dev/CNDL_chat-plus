package ru.gasada.cndlchatplus;

import java.util.List;

import net.minecraft.client.gui.Font;

public final class ChatContextMenu {
	private static final int ROW_HEIGHT = 14;
	private static final int PAD_X = 5;

	private String message;
	private ChatMessageSenderExtractor.Sender sender;
	private ChatTab channel;
	private String bookmarkText;
	private List<ChatContextAction> actions = List.of();
	private int x;
	private int y;
	private int width;

	public boolean open() {
		return !actions.isEmpty();
	}

	public void open(String message, String bookmarkText, ChatTab channel,
			ChatMessageSenderExtractor.Sender sender, List<ChatContextAction> actions,
			Font font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		this.message = message;
		this.bookmarkText = bookmarkText;
		this.channel = channel;
		this.sender = sender;
		this.actions = List.copyOf(actions);
		width = actions.stream().mapToInt(action -> font.width(action.label())).max().orElse(0) + PAD_X * 2;
		x = Math.clamp(mouseX, 0, Math.max(0, screenWidth - width));
		y = Math.clamp(mouseY, 0, Math.max(0, screenHeight - actions.size() * ROW_HEIGHT));
	}

	public void close() {
		actions = List.of();
		message = null;
		sender = null;
		channel = null;
		bookmarkText = null;
	}

	public void render(CompatGraphics graphics, Font font, int mouseX, int mouseY) {
		if (!open()) return;
		int height = actions.size() * ROW_HEIGHT;
		graphics.roundedRect(x, y, x + width, y + height, ThemeTokens.safeRadiusMedium(),
				ThemeTokens.safeBorderWidth(), ThemeTokens.border(), ThemeTokens.surface());
		for (int index = 0; index < actions.size(); index++) {
			int rowY = y + index * ROW_HEIGHT;
			boolean hovered = mouseX >= x && mouseX < x + width
					&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			if (hovered) graphics.roundedFill(x, rowY, x + width, rowY + ROW_HEIGHT,
					ThemeTokens.safeRadiusMedium(), ThemeTokens.surfaceHover());
			graphics.text(font, actions.get(index).label(), x + PAD_X, rowY + 3, ThemeTokens.text());
		}
	}

	public ChatContextAction actionAt(double mouseX, double mouseY) {
		if (!open() || mouseX < x || mouseX >= x + width || mouseY < y
				|| mouseY >= y + actions.size() * ROW_HEIGHT) {
			return null;
		}
		return actions.get((int) ((mouseY - y) / ROW_HEIGHT));
	}

	public String message() {
		return message;
	}

	public ChatMessageSenderExtractor.Sender sender() {
		return sender;
	}

	public ChatTab channel() {
		return channel;
	}

	public String bookmarkText() {
		return bookmarkText;
	}
}
