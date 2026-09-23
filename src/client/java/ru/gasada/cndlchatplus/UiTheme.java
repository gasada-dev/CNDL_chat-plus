package ru.gasada.cndlchatplus;

record UiTheme(Meta meta, Colors colors, Geometry geometry, Chat chat) {
	static UiTheme defaults() {
		return new UiTheme(new Meta("default", "CNDL Chat+", "CNDL_chat+", 1),
				new Colors(0xE010141D, 0xD9242B38, 0xD91C222E, 0xD9262E3D, 0xD9182131,
						0xFF536178, 0xFFA242F3, 0xFFA242F3, 0xFFB76EF5, 0xFFE8ECF2,
						0xFF9DA8B8, 0xFF75D98B, 0xFFFFCC66, 0xFFFF7777, 0xFF55FF55,
						0xB0181D27, 0xD0222937, 0xE03A2A38, 0xFFFF8FB5, 0xFFFF6FA5,
						0xFFFFE4EE, 0xA0101010, 0xA0000000, 0xC0404040, 0xFFFFFFFF,
						0xFFAAAAAA, 0xFFFFFFFF, 0xFFFF5555),
				new Geometry(2, 4, 7, 2, 4, 8, 16), new Chat(4, 1, 0, 0, 1.0, 12, 2, false));
	}

	record Meta(String id, String name, String author, int version) { }

	record Colors(int background, int surface, int surfaceSecondary, int hover, int pressed,
			int border, int borderFocused, int accent, int accentSoft, int text, int textMuted,
			int success, int warning, int danger, int online, int hudSurface, int noticeSurface,
			int marriageSurface, int marriageBorder, int marriageAccent, int marriageText,
			int chatBackground, int chatTabBackground, int chatTabBackgroundHover, int chatTabOutline,
			int chatTabText, int chatTabTextActive, int chatBadge) { }

	record Geometry(int radiusSmall, int radiusMedium, int radiusLarge, int borderWidth,
			int spacingSmall, int spacingMedium, int spacingLarge) { }

	record Chat(int padding, int messageSpacing, int width, int height, double backgroundOpacity, int tabHeight, int tabGap,
			boolean overrideBackground) { }
}
