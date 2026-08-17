package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WildcardMatcherTest {
	private final WildcardMatcher matcher = new WildcardMatcher();

	@Test
	void fullMatchSupportsEveryWildcardPositionAndMultipleWildcards() {
		assertTrue(matcher.matches("привет", "привет", WildcardMatchMode.FULL_MATCH));
		assertTrue(matcher.matches("привет*", "привет всем", WildcardMatchMode.FULL_MATCH));
		assertTrue(matcher.matches("*привет", "ну привет", WildcardMatchMode.FULL_MATCH));
		assertTrue(matcher.matches("*привет*", "ну привет всем", WildcardMatchMode.FULL_MATCH));
		assertTrue(matcher.matches("п*р*т", "привет", WildcardMatchMode.FULL_MATCH));
		assertFalse(matcher.matches("привет", "ну привет", WildcardMatchMode.FULL_MATCH));
	}

	@Test
	void containsMatchFindsPatternInsideText() {
		assertTrue(matcher.matches("казино*бонус", "до казино и бонус после",
				WildcardMatchMode.CONTAINS_MATCH));
		assertTrue(matcher.matches("реклама", "это реклама здесь", WildcardMatchMode.CONTAINS_MATCH));
		assertFalse(matcher.matches("реклама", "рек лам а", WildcardMatchMode.CONTAINS_MATCH));
	}

	@Test
	void preservesEmptyPatternCharacterization() {
		assertTrue(matcher.matches("", "", WildcardMatchMode.FULL_MATCH));
		assertFalse(matcher.matches("", "текст", WildcardMatchMode.FULL_MATCH));
		assertTrue(matcher.matches("", "любой текст", WildcardMatchMode.CONTAINS_MATCH));
		assertTrue(matcher.matches("   ", "любой текст", WildcardMatchMode.CONTAINS_MATCH));
	}

	@Test
	void handlesUnicodeCaseAndFullMatchWhitespaceNormalization() {
		assertTrue(matcher.matches("  ЁЖИК   ПРИВЕТ  ", "ёжик\t\nпривет", WildcardMatchMode.FULL_MATCH));
		assertTrue(matcher.matches("*ёжик*", "Сообщение про ЁЖИКА", WildcardMatchMode.CONTAINS_MATCH));
	}

	@Test
	void quotesRegexCharactersAndBackslash() {
		String literal = "цена.+? [x] (тест) ^$ \\";
		assertTrue(matcher.matches(literal, literal, WildcardMatchMode.FULL_MATCH));
		assertFalse(matcher.matches(literal, "ценаABC x", WildcardMatchMode.FULL_MATCH));
	}

	@Test
	void reusesCompiledPatternAndCompilesChangedSource() {
		CompiledWildcard first = matcher.compile("первый*", WildcardMatchMode.FULL_MATCH);
		assertSame(first, matcher.compile("первый*", WildcardMatchMode.FULL_MATCH));
		CompiledWildcard changed = matcher.compile("второй*", WildcardMatchMode.FULL_MATCH);
		assertFalse(changed.matches("первый текст"));
		assertTrue(changed.matches("второй текст"));
		assertEquals(2, matcher.cachedPatternCount());
	}

	@Test
	void cacheIsBoundedPerMatcherInstance() {
		WildcardMatcher small = new WildcardMatcher(2);
		small.compile("one", WildcardMatchMode.FULL_MATCH);
		small.compile("two", WildcardMatchMode.FULL_MATCH);
		small.compile("three", WildcardMatchMode.FULL_MATCH);
		assertEquals(2, small.cachedPatternCount());
	}
}
