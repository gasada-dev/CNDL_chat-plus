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
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("реклама"), "Это реклама здесь"));
		assertFalse(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("реклама"), "рек лам а"));
	}

	@Test
	void wildcardPatternUsesGlobSearchInsideText() {
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("казино*бонус"), "текст казино и большой бонус дальше"));
		assertFalse(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("казино*бонус"), "сначала бонус, затем казино"));
	}

	@Test
	void matchingIgnoresCase() {
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("ПрИвЕт"), "НУ ПРИВЕТ"));
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("*МИР*"), "Привет мир"));
	}

	@Test
	void emptyOrBlankPatternMatchesEveryTextAtMatcherLevel() {
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(List.of(""), "обычное сообщение"));
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(List.of("   "), "обычное сообщение"));
	}

	@Test
	void unicodeTextIsMatched() {
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("*ёжик*"), "Сообщение про ЁЖИКА"));
	}

	@Test
	void regexMetacharactersAreLiteral() {
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("цена.+?[x]"), "до цена.+?[x] после"));
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("начало.[x]*конец"), "начало.[x] любой конец"));
		assertFalse(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("цена.+?[x]"), "ценаABCx"));
	}

	@Test
	void anyMatchingFilterHidesTheMessage() {
		assertTrue(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("нет", "совпало", "тоже нет"), "здесь совпало"));
		assertFalse(GasadaChatResponderClient.matchesAnyMutedPattern(
				List.of("первый", "второй"), "третий"));
	}

	@Test
	void evaluationStopsAfterFirstMatch() {
		List<String> filters = new ArrayList<>();
		filters.add("совпало");
		filters.add(null);

		assertDoesNotThrow(() -> assertTrue(
				GasadaChatResponderClient.matchesAnyMutedPattern(filters, "совпало")));
	}
}
