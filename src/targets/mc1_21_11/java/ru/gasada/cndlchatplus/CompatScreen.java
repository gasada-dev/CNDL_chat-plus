package ru.gasada.cndlchatplus;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class CompatScreen extends Screen {
	protected CompatScreen(Component title) {
		super(title);
	}

	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) { }
	protected abstract void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta);

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		CompatGraphics compat = new CompatGraphics(graphics);
		renderBackgroundContent(compat, mouseX, mouseY, delta);
		renderContent(compat, mouseX, mouseY, delta);
		super.render(graphics, mouseX, mouseY, delta);
	}
}
