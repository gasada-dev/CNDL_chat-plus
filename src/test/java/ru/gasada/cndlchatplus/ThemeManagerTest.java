package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ThemeManagerTest {
	@TempDir
	Path directory;

	@Test
	void persistsSelectionAndReloadsTheme() throws Exception {
		ThemeManager.initialize(directory);
		Path themes = ThemeManager.themesDirectory();
		Files.writeString(themes.resolve("blue.json"), "{\"meta\":{\"id\":\"blue\"},\"colors\":{\"accent\":\"#123456\"}}");

		assertTrue(ThemeManager.setTheme("blue"));
		assertEquals("blue", Files.readString(themes.resolve("active.txt"), StandardCharsets.UTF_8));
		ThemeManager.reload();
		assertEquals(0xFF123456, ThemeManager.current().colors().accent());
	}

	@Test
	void missingOrBrokenThemeUsesDefault() throws Exception {
		ThemeManager.initialize(directory);
		assertEquals(UiTheme.defaults(), ThemeLoader.load(ThemeManager.themesDirectory().resolve("missing.json"), UiTheme.defaults()));
		assertTrue(ThemeManager.setTheme("vanilla"));
		UiTheme previous = ThemeManager.current();
		assertFalse(ThemeManager.setTheme("missing"));
		assertEquals(previous, ThemeManager.current());
		assertEquals("vanilla", Files.readString(ThemeManager.themesDirectory().resolve("active.txt"), StandardCharsets.UTF_8));
		Files.writeString(ThemeManager.themesDirectory().resolve("broken.json"), "{");
		assertTrue(ThemeManager.setTheme("broken"));
		assertEquals(UiTheme.defaults(), ThemeManager.current());
	}

	@Test
	void availableListsDefaultThenSortedUniqueThemes() throws Exception {
		ThemeManager.initialize(directory);
		Path themes = ThemeManager.themesDirectory();
		Files.writeString(themes.resolve("z.json"), "{\"meta\":{\"id\":\"z\"}}");
		Files.writeString(themes.resolve("a.json"), "{\"meta\":{\"id\":\"a\"}}");
		ThemeManager.reload();

		List<String> ids = ThemeManager.available().stream().map(UiTheme.Meta::id).toList();
		assertEquals("default", ids.getFirst());
		List<String> rest = ids.subList(1, ids.size());
		List<String> sorted = rest.stream().sorted().toList();
		assertEquals(sorted, rest);
		assertEquals(rest.size(), new java.util.LinkedHashSet<>(rest).size());
		assertTrue(rest.contains("a"));
		assertTrue(rest.contains("z"));
	}

	@Test
	void bundledDefaultCopiesParsesAndActivates() throws Exception {
		ThemeManager.initialize(directory);
		Path bundled = ThemeManager.themesDirectory().resolve("default.json");
		assertTrue(Files.isRegularFile(bundled));
		UiTheme fallback = UiTheme.defaults();
		UiTheme parsed = ThemeLoader.load(bundled, fallback);
		assertNotSame(fallback, parsed);
		assertTrue(ThemeManager.setTheme("default"));
		assertEquals(parsed, ThemeManager.current());
	}
}
