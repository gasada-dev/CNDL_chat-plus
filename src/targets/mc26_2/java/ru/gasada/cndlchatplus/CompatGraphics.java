package ru.gasada.cndlchatplus;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class CompatGraphics {
	private final GuiGraphicsExtractor graphics;

	public CompatGraphics(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}

	void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
	void roundedFill(int left, int top, int right, int bottom, int radius, int color) {
		int width = right - left;
		int height = bottom - top;
		if (width <= 0 || height <= 0) return;
		for (int row = 0; row < height; row++) {
			int inset = roundedInset(width, height, radius, row);
			fill(left + inset, top + row, right - inset, top + row + 1, color);
		}
	}
	void roundedRect(int left, int top, int right, int bottom, int radius, int borderWidth,
			int borderColor, int fillColor) {
		int width = right - left;
		int height = bottom - top;
		if (width <= 0 || height <= 0) return;
		int border = Math.clamp(borderWidth, 0, Math.min(width, height) / 2);
		roundedFill(left, top, right, bottom, radius, border > 0 ? borderColor : fillColor);
		if (border > 0 && width > border * 2 && height > border * 2) {
			roundedFill(left + border, top + border, right - border, bottom - border,
					Math.max(0, radius - border), fillColor);
		}
	}
	void roundedOutline(int left, int top, int right, int bottom, int radius, int borderWidth, int color) {
		int width = right - left;
		int height = bottom - top;
		if (width <= 0 || height <= 0) return;
		int border = Math.clamp(borderWidth, 0, Math.min(width, height) / 2);
		if (border == 0) return;
		for (int row = 0; row < height; row++) {
			int outer = roundedInset(width, height, radius, row);
			if (row < border || row >= height - border || width <= border * 2 || height <= border * 2) {
				fill(left + outer, top + row, right - outer, top + row + 1, color);
				continue;
			}
			int inner = roundedInset(width - border * 2, height - border * 2,
					Math.max(0, radius - border), row - border);
			fill(left + outer, top + row, left + border + inner, top + row + 1, color);
			fill(right - border - inner, top + row, right - outer, top + row + 1, color);
		}
	}
	static int roundedInset(int width, int height, int radius, int row) {
		if (width <= 0 || height <= 0 || row < 0 || row >= height) return 0;
		int rounded = Math.clamp(radius, 0, Math.min(width, height) / 2);
		int edge = Math.min(row, height - row - 1);
		if (rounded == 0 || edge >= rounded) return 0;
		int distance = rounded - edge - 1;
		return rounded - (int) Math.sqrt((long) rounded * rounded - (long) distance * distance);
	}
	void text(Font font, String text, int x, int y, int color) { graphics.text(font, text, x, y, color); }
	void text(Font font, Component text, int x, int y, int color) { graphics.text(font, text, x, y, color); }
	void text(Font font, FormattedCharSequence text, int x, int y, int color) { graphics.text(font, text, x, y, color); }
	void centeredText(Font font, String text, int x, int y, int color) { graphics.centeredText(font, text, x, y, color); }
	void centeredText(Font font, Component text, int x, int y, int color) { graphics.centeredText(font, text, x, y, color); }
	void textWithWordWrap(Font font, Component text, int x, int y, int width, int color) {
		graphics.textWithWordWrap(font, text, x, y, width, color);
	}
	void outline(int x, int y, int width, int height, int color) { graphics.outline(x, y, width, height, color); }
	int guiWidth() { return graphics.guiWidth(); }
	int guiHeight() { return graphics.guiHeight(); }
	void pushPose() { graphics.pose().pushMatrix(); }
	void translatePose(float x, float y) { graphics.pose().translate(x, y); }
	void scalePose(float scale) { graphics.pose().scale(scale); }
	void popPose() { graphics.pose().popMatrix(); }
}
