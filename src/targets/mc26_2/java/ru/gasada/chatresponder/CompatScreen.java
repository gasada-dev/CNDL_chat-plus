package ru.gasada.chatresponder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class CompatScreen extends Screen {
	protected CompatScreen(Component title) {
		super(title);
	}

	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) { }
	protected abstract void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta);

	@Override
	public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		renderBackgroundContent(new CompatGraphics(graphics), mouseX, mouseY, delta);
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		renderContent(new CompatGraphics(graphics), mouseX, mouseY, delta);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}
}
