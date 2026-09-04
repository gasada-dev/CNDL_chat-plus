package ru.gasada.cndlchatplus;

import java.util.List;

import net.minecraft.client.gui.Font;

public final class ChatContextMenu {
	private static final int ROW_HEIGHT = 14;
	private static final int PAD_X = 5;
	private static final int BG = 0xE0101010;
	private static final int HOVER = 0xE0404040;
	private static final int BORDER = 0xFFAAAAAA;
	private static final int TEXT = 0xFFFFFFFF;

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
		for (int index = 0; index < actions.size(); index++) {
			int rowY = y + index * ROW_HEIGHT;
			boolean hovered = mouseX >= x && mouseX < x + width
					&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
			graphics.fill(x, rowY, x + width, rowY + ROW_HEIGHT, hovered ? HOVER : BG);
			graphics.text(font, actions.get(index).label(), x + PAD_X, rowY + 3, TEXT);
		}
		graphics.outline(x, y, width, actions.size() * ROW_HEIGHT, BORDER);
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
