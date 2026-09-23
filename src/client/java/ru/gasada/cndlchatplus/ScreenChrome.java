package ru.gasada.cndlchatplus;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

final class ScreenChrome {
	private ScreenChrome() {
	}

	static void drawBackground(CompatGraphics graphics, int width, int height) {
		graphics.fill(0, 0, width, height, ThemeTokens.background());
	}

	static void drawPanel(CompatGraphics graphics, int x, int y, int width, int height) {
		drawPanel(graphics, x, y, width, height, ThemeTokens.surface(), ThemeTokens.border());
	}

	static void drawPanel(CompatGraphics graphics, int x, int y, int width, int height,
			int surfaceColor, int borderColor) {
		int border = ThemeTokens.safeBorderWidth();
		graphics.roundedRect(x - border, y - border, x + width + border, y + height + border,
				ThemeTokens.safeRadiusLarge(), border, borderColor, surfaceColor);
	}

	static void drawHeader(CompatGraphics graphics, Font font, Component title, int centerX, int y) {
		int titleY = y + ThemeTokens.safeSpacingLarge() - 16;
		graphics.centeredText(font, title, centerX, titleY, ThemeTokens.text());
		int halfWidth = Math.max(0, font.width(title) / 2 + ThemeTokens.safeSpacingSmall() - 4);
		int underlineY = titleY + ThemeTokens.safeSpacingMedium() + 4;
		graphics.fill(centerX - halfWidth, underlineY, centerX + halfWidth, underlineY + 1, ThemeTokens.accent());
	}
}
