package ru.gasada.cndlchatplus;

import java.util.List;

public final class ChatTextSelection {
	private static final int BOTTOM_MARGIN = 40;

	public record Point(int line, int offset) {
	}

	public record Range(int start, int end) { }

	public static Range range(String text, int line, Point first, Point second) {
		Point top = first.line() >= second.line() ? first : second;
		Point bottom = top == first ? second : first;
		if (line < bottom.line() || line > top.line()) return new Range(0, 0);
		if (top.line() == bottom.line()) {
			return new Range(clampOffset(text, Math.min(first.offset(), second.offset())),
					clampOffset(text, Math.max(first.offset(), second.offset())));
		}
		return new Range(line == top.line() ? clampOffset(text, top.offset()) : 0,
				line == bottom.line() ? clampOffset(text, bottom.offset()) : text.length());
	}

	private ChatTextSelection() {
	}

	public static boolean contains(double mouseX, double mouseY, int screenHeight, int chatWidth,
			int chatHeight, double scale, int lineHeight, int padding) {
		if (scale <= 0 || lineHeight <= 0 || chatHeight <= 0) return false;
		double localX = mouseX / scale - Math.max(0, padding);
		if (localX < 0 || localX >= chatWidth / scale) return false;
		int chatBottom = (int) Math.floor((screenHeight - BOTTOM_MARGIN) / scale);
		int row = (int) Math.floor((chatBottom - mouseY / scale) / lineHeight);
		return row >= 0 && row < chatHeight / lineHeight;
	}

	public static Point pointAt(double mouseX, double mouseY, int screenHeight, int chatHeight, double scale,
			int lineHeight, int scrollPosition, List<String> lines, List<int[]> advances, int padding) {
		if (scale <= 0 || lineHeight <= 0 || lines.isEmpty() || advances.size() != lines.size()) return null;
		int firstVisible = Math.clamp(scrollPosition, 0, lines.size() - 1);
		int visibleLines = Math.min(chatHeight / lineHeight, lines.size() - firstVisible);
		if (visibleLines <= 0) return null;
		int chatBottom = (int) Math.floor((screenHeight - BOTTOM_MARGIN) / scale);
		int row = (int) Math.floor((chatBottom - mouseY / scale) / lineHeight);
		int line = firstVisible + Math.clamp(row, 0, visibleLines - 1);
		return new Point(line, offsetAt(mouseX / scale - Math.max(0, padding), advances.get(line)));
	}

	public static String selectedText(List<String> lines, Point first, Point second) {
		if (first == null || second == null || lines.isEmpty()) return "";
		Point top = first.line() >= second.line() ? first : second;
		Point bottom = first.line() >= second.line() ? second : first;
		if (top.line() < 0 || top.line() >= lines.size() || bottom.line() < 0 || bottom.line() >= lines.size()) {
			return "";
		}
		StringBuilder selected = new StringBuilder();
		for (int line = top.line(); line >= bottom.line(); line--) {
			String text = lines.get(line);
			Range range = range(text, line, first, second);
			if (line != top.line()) selected.append('\n');
			selected.append(text, range.start(), range.end());
		}
		return selected.toString();
	}

	public static int lineTop(int screenHeight, double scale, int lineHeight, int scrollPosition, int line) {
		int chatBottom = (int) Math.floor((screenHeight - BOTTOM_MARGIN) / scale);
		return (int) Math.floor((chatBottom - (line - scrollPosition + 1) * lineHeight) * scale);
	}

	private static int offsetAt(double x, int[] advances) {
		if (advances.length == 0 || x <= 0) return 0;
		for (int offset = 1; offset < advances.length; offset++) {
			if (x < advances[offset]) return offset - 1;
		}
		return advances.length - 1;
	}

	private static int clampOffset(String text, int offset) {
		return Math.clamp(offset, 0, text.length());
	}
}
