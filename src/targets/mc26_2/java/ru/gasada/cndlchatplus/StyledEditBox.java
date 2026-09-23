package ru.gasada.cndlchatplus;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class StyledEditBox extends EditBox {
	StyledEditBox(Font font, int x, int y, int width, int height, Component narration) {
		super(font, x, y, width, height, narration);
		setTextColor(ThemeTokens.text());
		setTextColorUneditable(ThemeTokens.textMuted());
		setBordered(ThemeTokens.safeBorderWidth() > 0);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
		int border = ThemeTokens.safeBorderWidth();
		int color = isFocused() ? ThemeTokens.accent() : ThemeTokens.border();
		int left = getX() - 1;
		int top = getY() - 1;
		int right = getX() + getWidth() + 1;
		int bottom = getY() + getHeight() + 1;
		new CompatGraphics(graphics).roundedOutline(left, top, right, bottom,
				ThemeTokens.safeRadiusSmall(), border, color);
	}
}
