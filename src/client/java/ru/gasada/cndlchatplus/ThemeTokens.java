package ru.gasada.cndlchatplus;

public final class ThemeTokens {
	public static final int FIELD_HEIGHT = 20;
	public static final int ROW_HEIGHT = 28;

	private ThemeTokens() {
	}

	public static int background() { return ThemeManager.current().colors().background(); }
	public static int surface() { return ThemeManager.current().colors().surface(); }
	public static int surfaceSecondary() { return ThemeManager.current().colors().surfaceSecondary(); }
	public static int surfaceHover() { return ThemeManager.current().colors().hover(); }
	public static int surfacePressed() { return ThemeManager.current().colors().pressed(); }
	public static int border() { return ThemeManager.current().colors().border(); }
	public static int borderFocused() { return ThemeManager.current().colors().borderFocused(); }
	public static int accent() { return ThemeManager.current().colors().accent(); }
	public static int accentSoft() { return ThemeManager.current().colors().accentSoft(); }
	public static int text() { return ThemeManager.current().colors().text(); }
	public static int textMuted() { return ThemeManager.current().colors().textMuted(); }
	public static int success() { return ThemeManager.current().colors().success(); }
	public static int warning() { return ThemeManager.current().colors().warning(); }
	public static int danger() { return ThemeManager.current().colors().danger(); }
	public static int online() { return ThemeManager.current().colors().online(); }
	public static int hudSurface() { return ThemeManager.current().colors().hudSurface(); }
	public static int noticeSurface() { return ThemeManager.current().colors().noticeSurface(); }
	public static int marriageSurface() { return ThemeManager.current().colors().marriageSurface(); }
	public static int marriageBorder() { return ThemeManager.current().colors().marriageBorder(); }
	public static int marriageAccent() { return ThemeManager.current().colors().marriageAccent(); }
	public static int marriageText() { return ThemeManager.current().colors().marriageText(); }
	public static int chatTabBackground() { return ThemeManager.current().colors().chatTabBackground(); }
	public static int chatTabBackgroundHover() { return ThemeManager.current().colors().chatTabBackgroundHover(); }
	public static int chatTabOutline() { return ThemeManager.current().colors().chatTabOutline(); }
	public static int chatTabText() { return ThemeManager.current().colors().chatTabText(); }
	public static int chatTabTextActive() { return ThemeManager.current().colors().chatTabTextActive(); }
	public static int chatBadge() { return ThemeManager.current().colors().chatBadge(); }
	public static int chatBackground() { return ThemeManager.current().colors().chatBackground(); }
	public static double chatBackgroundOpacity() { return ThemeManager.current().chat().backgroundOpacity(); }
	public static boolean chatOverrideBackground() { return ThemeManager.current().chat().overrideBackground(); }
	public static int chatPadding() { return ThemeManager.current().chat().padding(); }
	public static int chatMessageSpacing() { return ThemeManager.current().chat().messageSpacing(); }
	public static int chatWidth() { return ThemeManager.current().chat().width(); }
	public static int chatHeight() { return ThemeManager.current().chat().height(); }
	public static int tabHeight() { return Math.clamp(ThemeManager.current().chat().tabHeight(), 9, 40); }
	public static int tabGap() { return Math.clamp(ThemeManager.current().chat().tabGap(), 0, 32); }
	public static int borderWidth() { return ThemeManager.current().geometry().borderWidth(); }
	public static int spacingSmall() { return ThemeManager.current().geometry().spacingSmall(); }
	public static int spacingMedium() { return ThemeManager.current().geometry().spacingMedium(); }
	public static int spacingLarge() { return ThemeManager.current().geometry().spacingLarge(); }
	public static int radiusSmall() { return ThemeManager.current().geometry().radiusSmall(); }
	public static int radiusMedium() { return ThemeManager.current().geometry().radiusMedium(); }
	public static int radiusLarge() { return ThemeManager.current().geometry().radiusLarge(); }

	public static int resolvedChatWidth(int vanillaWidth) {
		int width = chatWidth();
		return width <= 0 ? vanillaWidth : Math.clamp(width, 40, 320);
	}

	public static int resolvedChatHeight(int vanillaHeight) {
		int height = chatHeight();
		return height <= 0 ? vanillaHeight : Math.clamp(height, 20, 180);
	}

	public static int chatIndent() { return Math.clamp(chatPadding(), 0, 32); }
	public static int chatLineBaseHeight() { return 8 + Math.clamp(chatMessageSpacing(), 0, 32); }
	public static int themedChatBackground(int vanillaColor) {
		if (!chatOverrideBackground()) return vanillaColor;
		int argb = chatBackground();
		double opacity = chatBackgroundOpacity();
		if (!Double.isFinite(opacity)) opacity = 1;
		int alpha = (argb >>> 24) & 0xFF;
		alpha = (int) Math.round(alpha * Math.clamp(opacity, 0, 1));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}
	static int safeBorderWidth() { return Math.max(0, borderWidth()); }
	static int safeRadiusSmall() { return Math.max(0, radiusSmall()); }
	static int safeRadiusMedium() { return Math.max(0, radiusMedium()); }
	static int safeRadiusLarge() { return Math.max(0, radiusLarge()); }
	static int safeSpacingSmall() { return Math.clamp(spacingSmall(), 0, 64); }
	static int safeSpacingMedium() { return Math.clamp(spacingMedium(), 0, 64); }
	static int safeSpacingLarge() { return Math.clamp(spacingLarge(), 0, 64); }
}
