package ru.gasada.cndlchatplus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

class StyledButton extends Button {
	protected StyledButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	static Builder create(Component message, OnPress onPress) {
		return new Builder(message, onPress);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean highlighted = active && isHovered();
		new CompatGraphics(graphics).roundedRect(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
				ThemeTokens.safeRadiusSmall(), ThemeTokens.safeBorderWidth(),
				highlighted ? ThemeTokens.accent() : ThemeTokens.border(),
				highlighted ? ThemeTokens.surfaceHover() : ThemeTokens.surfaceSecondary());
		int color = active ? ThemeTokens.text() : ThemeTokens.textMuted();
		graphics.centeredText(Minecraft.getInstance().font, getMessage(),
				getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
	}

	static final class Builder {
		private final Component message;
		private final OnPress onPress;
		private Tooltip tooltip;
		private int x;
		private int y;
		private int width = 150;
		private int height = 20;

		Builder(Component message, OnPress onPress) {
			this.message = message;
			this.onPress = onPress;
		}

		Builder bounds(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			return this;
		}

		Builder tooltip(Tooltip tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		StyledButton build() {
			StyledButton button = new StyledButton(x, y, width, height, message, onPress);
			button.setTooltip(tooltip);
			return button;
		}
	}
}
