package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WildcardMatcherCharacterizationTest {
	@Test
	void exactPatternMatchesOnlyWholeNormalizedText() {
		assertTrue(ChatResponderEngine.wildcardMatches("привет", "привет"));
		assertFalse(ChatResponderEngine.wildcardMatches("привет", "ну привет"));
		assertFalse(ChatResponderEngine.wildcardMatches("привет", "привет всем"));
	}

	@Test
	void trailingWildcardMatchesPrefix() {
		assertTrue(ChatResponderEngine.wildcardMatches("привет*", "привет"));
		assertTrue(ChatResponderEngine.wildcardMatches("привет*", "привет всем"));
		assertFalse(ChatResponderEngine.wildcardMatches("привет*", "ну привет"));
	}

	@Test
	void leadingWildcardMatchesSuffix() {
		assertTrue(ChatResponderEngine.wildcardMatches("*привет", "привет"));
		assertTrue(ChatResponderEngine.wildcardMatches("*привет", "ну привет"));
		assertFalse(ChatResponderEngine.wildcardMatches("*привет", "привет всем"));
	}

	@Test
	void surroundingWildcardsMatchContainedText() {
		assertTrue(ChatResponderEngine.wildcardMatches("*привет*", "ну привет всем"));
		assertFalse(ChatResponderEngine.wildcardMatches("*привет*", "добрый день"));
	}

	@Test
	void singleWildcardMatchesEveryStringIncludingEmptyAtHelperLevel() {
		assertTrue(ChatResponderEngine.wildcardMatches("*", "любой текст"));
		assertTrue(ChatResponderEngine.wildcardMatches("*", ""));
	}

	@Test
	void emptyPatternMatchesOnlyEmptyNormalizedText() {
		assertTrue(ChatResponderEngine.wildcardMatches("", ""));
		assertTrue(ChatResponderEngine.wildcardMatches("   ", " \t "));
		assertFalse(ChatResponderEngine.wildcardMatches("", "текст"));
	}

	@Test
	void matchingIgnoresCaseAndCollapsesWhitespace() {
		assertTrue(ChatResponderEngine.wildcardMatches("  ПрИвЕт   мир  ", "привет\t\nмир"));
	}

	@Test
	void regexMetacharactersOtherThanWildcardAreLiteral() {
		String literal = "цена.+? [один] (тест) ^$ \\";
		assertTrue(ChatResponderEngine.wildcardMatches(literal, literal));
		assertFalse(ChatResponderEngine.wildcardMatches(literal, "ценаXYZ [один] (тест) ^$ \\"));
	}
}
