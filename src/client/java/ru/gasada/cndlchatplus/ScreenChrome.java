package ru.gasada.cndlchatplus;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class ScreenChrome {
	private ScreenChrome() {
	}

	static void drawBackground(CompatGraphics graphics, int width, int height) {
		graphics.fill(0, 0, width, height, UiConstants.BACKGROUND);
	}

	static void drawPanel(CompatGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, UiConstants.BORDER);
		graphics.fill(x, y, x + width, y + height, UiConstants.SURFACE);
	}

	static void drawHeader(CompatGraphics graphics, Font font, Component title, int centerX, int y) {
		graphics.centeredText(font, title, centerX, y, UiConstants.TEXT);
		int halfWidth = font.width(title) / 2;
		graphics.fill(centerX - halfWidth, y + 12, centerX + halfWidth, y + 13, UiConstants.ACCENT);
	}
}
