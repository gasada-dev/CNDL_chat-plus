package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ThemeTokensTest {
	@TempDir
	Path directory;

	@Test
	void defaultAndVanillaThemesPreserveVanillaChatGeometryAndBackgroundAlpha() {
		ThemeManager.initialize(directory);
		int vanillaBackground = 0x43123456;

		assertEquals(4, ThemeTokens.chatIndent());
		assertEquals(9, ThemeTokens.chatLineBaseHeight());
		assertEquals(vanillaBackground, ThemeTokens.themedChatBackground(vanillaBackground));
		assertTrue(ThemeManager.setTheme("vanilla"));
		assertEquals(vanillaBackground, ThemeTokens.themedChatBackground(vanillaBackground));
	}

	@Test
	void currentIsNeverNullAndTokensFollowSelectedTheme() throws Exception {
		assertNotNull(ThemeManager.current());
		ThemeManager.initialize(directory);
		Files.writeString(ThemeManager.themesDirectory().resolve("token.json"), """
				{"meta":{"id":"token"},"colors":{"accent":"#123456","chatBackground":"#80123456"},
				 "geometry":{"radiusLarge":11},"chat":{"padding":7,"messageSpacing":3,"width":999,
				 "height":1,"backgroundOpacity":0,"tabHeight":2,"tabGap":-1,"overrideBackground":true}}
				""");

		assertTrue(ThemeManager.setTheme("token"));
		assertEquals(0xFF123456, ThemeTokens.accent());
		assertEquals(0x80123456, ThemeTokens.chatBackground());
		assertEquals(11, ThemeTokens.radiusLarge());
		assertEquals(7, ThemeTokens.chatPadding());
		assertEquals(320, ThemeTokens.resolvedChatWidth(200));
		assertEquals(20, ThemeTokens.resolvedChatHeight(100));
		assertEquals(11, ThemeTokens.chatLineBaseHeight());
		assertEquals(9, ThemeTokens.tabHeight());
		assertEquals(0, ThemeTokens.tabGap());
		assertEquals(0x00123456, ThemeTokens.themedChatBackground(0xFFFFFFFF));
		assertTrue(ThemeTokens.chatOverrideBackground());
	}
}
