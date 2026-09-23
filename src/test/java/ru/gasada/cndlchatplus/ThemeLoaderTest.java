package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ThemeLoaderTest {
	@TempDir
	Path directory;

	@Test
	void parsesKnownTokensAndIgnoresUnknownFields() {
		UiTheme theme = ThemeLoader.parse("""
				{"meta":{"id":"test","name":"Test","author":"Author","version":2},
				 "colors":{"background":"#123456","surface":"#80123456","unknown":"#FFFFFFFF"},
				 "geometry":{"radiusSmall":9},"chat":{"overrideBackground":true,"backgroundOpacity":0.4},"unknown":true}
				""", UiTheme.defaults());

		assertEquals("test", theme.meta().id());
		assertEquals(0xFF123456, theme.colors().background());
		assertEquals(0x80123456, theme.colors().surface());
		assertEquals(9, theme.geometry().radiusSmall());
		assertEquals(0.4, theme.chat().backgroundOpacity());
		assertEquals(UiTheme.defaults().colors().accent(), theme.colors().accent());
	}

	@Test
	void brokenJsonAndAbsentFileUseFallback() {
		UiTheme fallback = UiTheme.defaults();
		assertSame(fallback, ThemeLoader.parse("{", fallback));
		assertSame(fallback, ThemeLoader.load(directory.resolve("missing.json"), fallback));
	}

	@Test
	void parsesChatWidthAndHeightAndFallsBackWhenMissing() {
		UiTheme fallback = UiTheme.defaults();
		UiTheme sized = ThemeLoader.parse("{\"chat\":{\"width\":320,\"height\":180}}", fallback);
		UiTheme missing = ThemeLoader.parse("{\"chat\":{}}", fallback);
		UiTheme invalid = ThemeLoader.parse("{\"chat\":{\"width\":\"wide\",\"height\":true}}", fallback);

		assertEquals(320, sized.chat().width());
		assertEquals(180, sized.chat().height());
		assertEquals(fallback.chat().width(), missing.chat().width());
		assertEquals(fallback.chat().height(), missing.chat().height());
		assertEquals(fallback.chat().width(), invalid.chat().width());
		assertEquals(fallback.chat().height(), invalid.chat().height());
	}

	@Test
	void scanSkipsBrokenFilesAndUsesFileNameForMissingId() throws Exception {
		Path themes = directory.resolve("themes");
		java.nio.file.Files.createDirectories(themes);
		java.nio.file.Files.writeString(themes.resolve("named.json"), "{\"meta\":{\"name\":\"Named\"}}");
		java.nio.file.Files.writeString(themes.resolve("broken.json"), "{");

		List<UiTheme> themesFound = ThemeLoader.scan(themes);
		assertEquals(1, themesFound.size());
		assertEquals("named", themesFound.getFirst().meta().id());
	}
}
