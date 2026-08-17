package ru.gasada.chatresponder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class StyledEditBox extends EditBox {
	StyledEditBox(Font font, int x, int y, int width, int height, Component narration) {
		super(font, x, y, width, height, narration);
		setTextColor(UiConstants.TEXT);
		setTextColorUneditable(UiConstants.MUTED);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
		graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2,
				isFocused() ? UiConstants.ACCENT : UiConstants.BORDER);
	}
}
