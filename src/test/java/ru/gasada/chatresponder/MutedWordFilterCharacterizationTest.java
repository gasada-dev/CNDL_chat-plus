package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class MutedWordFilterCharacterizationTest {
	@Test
	void plainPatternUsesSubstringSearch() {
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("реклама"), "Это реклама здесь"));
		assertFalse(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("реклама"), "рек лам а"));
	}

	@Test
	void wildcardPatternUsesGlobSearchInsideText() {
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("казино*бонус"), "текст казино и большой бонус дальше"));
		assertFalse(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("казино*бонус"), "сначала бонус, затем казино"));
	}

	@Test
	void matchingIgnoresCase() {
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("ПрИвЕт"), "НУ ПРИВЕТ"));
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("*МИР*"), "Привет мир"));
	}

	@Test
	void emptyOrBlankPatternMatchesEveryTextAtMatcherLevel() {
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(List.of(""), "обычное сообщение"));
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(List.of("   "), "обычное сообщение"));
	}

	@Test
	void unicodeTextIsMatched() {
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("*ёжик*"), "Сообщение про ЁЖИКА"));
	}

	@Test
	void regexMetacharactersAreLiteral() {
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("цена.+?[x]"), "до цена.+?[x] после"));
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("начало.[x]*конец"), "начало.[x] любой конец"));
		assertFalse(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("цена.+?[x]"), "ценаABCx"));
	}

	@Test
	void anyMatchingFilterHidesTheMessage() {
		assertTrue(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("нет", "совпало", "тоже нет"), "здесь совпало"));
		assertFalse(CompiledFilterSet.matchesAnyMutedPattern(
				List.of("первый", "второй"), "третий"));
	}

	@Test
	void evaluationStopsAfterFirstMatch() {
		List<String> filters = new ArrayList<>();
		filters.add("совпало");
		filters.add(null);

		assertDoesNotThrow(() -> assertTrue(
				CompiledFilterSet.matchesAnyMutedPattern(filters, "совпало")));
	}
}
