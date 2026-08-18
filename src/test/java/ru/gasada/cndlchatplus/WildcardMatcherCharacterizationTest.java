package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WildcardMatcherCharacterizationTest {
	private final WildcardMatcher matcher = new WildcardMatcher();

	@Test
	void exactPatternMatchesOnlyWholeNormalizedText() {
		assertTrue(matches("привет", "привет"));
		assertFalse(matches("привет", "ну привет"));
		assertFalse(matches("привет", "привет всем"));
	}

	@Test
	void trailingWildcardMatchesPrefix() {
		assertTrue(matches("привет*", "привет"));
		assertTrue(matches("привет*", "привет всем"));
		assertFalse(matches("привет*", "ну привет"));
	}

	@Test
	void leadingWildcardMatchesSuffix() {
		assertTrue(matches("*привет", "привет"));
		assertTrue(matches("*привет", "ну привет"));
		assertFalse(matches("*привет", "привет всем"));
	}

	@Test
	void surroundingWildcardsMatchContainedText() {
		assertTrue(matches("*привет*", "ну привет всем"));
		assertFalse(matches("*привет*", "добрый день"));
	}

	@Test
	void singleWildcardMatchesEveryStringIncludingEmptyAtHelperLevel() {
		assertTrue(matches("*", "любой текст"));
		assertTrue(matches("*", ""));
	}

	@Test
	void emptyPatternMatchesOnlyEmptyNormalizedText() {
		assertTrue(matches("", ""));
		assertTrue(matches("   ", " \t "));
		assertFalse(matches("", "текст"));
	}

	@Test
	void matchingIgnoresCaseAndCollapsesWhitespace() {
		assertTrue(matches("  ПрИвЕт   мир  ", "привет\t\nмир"));
	}

	@Test
	void regexMetacharactersOtherThanWildcardAreLiteral() {
		String literal = "цена.+? [один] (тест) ^$ \\";
		assertTrue(matches(literal, literal));
		assertFalse(matches(literal, "ценаXYZ [один] (тест) ^$ \\"));
	}

	private boolean matches(String pattern, String text) {
		return matcher.matches(pattern, text, WildcardMatchMode.FULL_MATCH);
	}
}
