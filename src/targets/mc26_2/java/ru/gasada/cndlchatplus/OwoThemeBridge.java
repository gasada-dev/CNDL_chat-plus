package ru.gasada.cndlchatplus;

import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Surface;

final class OwoThemeBridge {
	private OwoThemeBridge() { }

	static Color color(int argb) { return Color.ofArgb(argb); }
	static Surface panel() { return panel(ThemeTokens.border()); }
	static Surface accentPanel() { return panel(ThemeTokens.borderFocused()); }
	static Insets padding() { return Insets.of(ThemeTokens.safeSpacingMedium()); }

	private static Surface panel(int borderColor) {
		return (graphics, component) -> new CompatGraphics(graphics).roundedRect(component.x(), component.y(),
				component.x() + component.width(), component.y() + component.height(), ThemeTokens.safeRadiusMedium(),
				ThemeTokens.safeBorderWidth(), borderColor, ThemeTokens.surface());
	}
}
