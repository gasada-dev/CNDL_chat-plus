package ru.gasada.cndlchatplus;

public final class ChatMessageHitTest {
	private static final int BOTTOM_MARGIN = 40;

	private ChatMessageHitTest() {
	}

	public static int lineIndex(double mouseX, double mouseY, int screenHeight, int chatWidth,
			int chatHeight, double scale, int lineHeight, int scrollPosition) {
		if (scale <= 0 || lineHeight <= 0) return -1;
		double localX = mouseX / scale - 4;
		if (localX < 0 || localX >= chatWidth / scale) return -1;
		int chatBottom = (int) Math.floor((screenHeight - BOTTOM_MARGIN) / scale);
		int row = (int) Math.floor((chatBottom - mouseY / scale) / lineHeight);
		if (row < 0 || row >= chatHeight / lineHeight) return -1;
		return row + scrollPosition;
	}
}
