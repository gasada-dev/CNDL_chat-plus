package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class CompiledFilterSetTest {
	@Test
	void compiledFiltersSearchInsideTextAndReturnFirstMatchingSource() {
		CompiledFilterSet filters = CompiledFilterSet.compile(List.of("нет", "КаЗиНо*бонус", "бонус"));

		assertEquals("КаЗиНо*бонус", filters.firstMutedWord("до КАЗИНО и большой БОНУС после").orElseThrow());
		assertEquals("бонус", filters.firstMutedWord("сначала бонус, затем казино").orElseThrow());
		assertTrue(filters.firstMutedWord("обычное сообщение").isEmpty());
		assertTrue(CompiledFilterSet.compile(List.of()).firstMutedWord("текст").isEmpty());
	}

	@Test
	void compiledFiltersKeepBlankPatternsAndRegexCharactersLiteral() {
		CompiledFilterSet filters = CompiledFilterSet.compile(List.of("цена.+?[x]", "начало.[x]*конец"));

		assertEquals("цена.+?[x]", filters.firstMutedWord("до цена.+?[x] после").orElseThrow());
		assertEquals("начало.[x]*конец", filters.firstMutedWord("начало.[x] любой конец").orElseThrow());
		assertTrue(filters.firstMutedWord("ценаABCx").isEmpty());
		assertEquals("", CompiledFilterSet.compile(List.of("")).firstMutedWord("текст").orElseThrow());
		assertEquals("   ", CompiledFilterSet.compile(List.of("   ")).firstMutedWord("текст").orElseThrow());
	}
}
