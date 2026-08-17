package ru.gasada.cndlchatplus;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class CompatGraphics {
	private final GuiGraphicsExtractor graphics;

	public CompatGraphics(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}

	void fill(int left, int top, int right, int bottom, int color) { graphics.fill(left, top, right, bottom, color); }
	void text(Font font, String text, int x, int y, int color) { graphics.text(font, text, x, y, color); }
	void text(Font font, Component text, int x, int y, int color) { graphics.text(font, text, x, y, color); }
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
