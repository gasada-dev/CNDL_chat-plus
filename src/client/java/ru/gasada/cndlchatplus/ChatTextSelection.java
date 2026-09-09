package ru.gasada.cndlchatplus;

import java.util.List;

public final class ChatTextSelection {
	private static final int BOTTOM_MARGIN = 40;

	public record Point(int line, int offset) {
	}

	private ChatTextSelection() {
	}

	public static boolean contains(double mouseX, double mouseY, int screenHeight, int chatWidth,
			int chatHeight, double scale, int lineHeight) {
		if (scale <= 0 || lineHeight <= 0 || chatHeight <= 0) return false;
		double localX = mouseX / scale - 4;
		if (localX < 0 || localX >= chatWidth / scale) return false;
		int chatBottom = (int) Math.floor((screenHeight - BOTTOM_MARGIN) / scale);
		int row = (int) Math.floor((chatBottom - mouseY / scale) / lineHeight);
		return row >= 0 && row < chatHeight / lineHeight;
	}

	public static Point pointAt(double mouseX, double mouseY, int screenHeight, int chatHeight, double scale,
			int lineHeight, int scrollPosition, List<String> lines, List<int[]> advances) {
		if (scale <= 0 || lineHeight <= 0 || lines.isEmpty() || advances.size() != lines.size()) return null;
		int firstVisible = Math.clamp(scrollPosition, 0, lines.size() - 1);
		int visibleLines = Math.min(chatHeight / lineHeight, lines.size() - firstVisible);
		if (visibleLines <= 0) return null;
		int chatBottom = (int) Math.floor((screenHeight - BOTTOM_MARGIN) / scale);
		int row = (int) Math.floor((chatBottom - mouseY / scale) / lineHeight);
		int line = firstVisible + Math.clamp(row, 0, visibleLines - 1);
		return new Point(line, offsetAt(mouseX / scale - 4, advances.get(line)));
	}

	public static String selectedText(List<String> lines, Point first, Point second) {
		if (first == null || second == null || lines.isEmpty()) return "";
		Point top = first.line() >= second.line() ? first : second;
		Point bottom = first.line() >= second.line() ? second : first;
		if (top.line() < 0 || top.line() >= lines.size() || bottom.line() < 0 || bottom.line() >= lines.size()) {
			return "";
		}
		int topOffset = clampOffset(lines.get(top.line()), top.offset());
		int bottomOffset = clampOffset(lines.get(bottom.line()), bottom.offset());
		if (top.line() == bottom.line()) {
			return lines.get(top.line()).substring(Math.min(topOffset, bottomOffset), Math.max(topOffset, bottomOffset));
		}
		StringBuilder selected = new StringBuilder();
		for (int line = top.line(); line >= bottom.line(); line--) {
			String text = lines.get(line);
			int start = line == bottom.line() ? bottomOffset : 0;
			int end = line == top.line() ? topOffset : text.length();
			if (!selected.isEmpty()) selected.append('\n');
			selected.append(text, start, end);
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
