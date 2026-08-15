package ru.gasada.chatresponder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class InvisibleButton extends AbstractWidget {
	private final Runnable action;

	InvisibleButton(int x, int y, int width, int height, Runnable action) {
		super(x, y, width, height, Component.empty());
		this.action = action;
	}

	@Override public void onClick(MouseButtonEvent event, boolean doubleClick) { action.run(); }
	@Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) { }
	@Override protected void updateWidgetNarration(NarrationElementOutput output) { }
}
