package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChatSearchStateTest {
	@Test
	void emptyAndNullQueriesMatchEverything() {
		ChatSearchState search = new ChatSearchState(() -> true);
		search.activate();

		search.setQuery(null);
		assertTrue(search.matches("message"));
		search.setQuery("   ");
		assertTrue(search.matches("message"));
	}

	@Test
	void matchesTrimmedQueryIgnoringCase() {
		ChatSearchState search = new ChatSearchState(() -> true);
		search.activate();
		search.setQuery("  ПРИВЕТ  ");

		assertTrue(search.matches("Ну привет, мир"));
		assertFalse(search.matches("Другая строка"));
	}

	@Test
	void inactiveOrDisabledSearchMatchesEverything() {
		ChatSearchState inactive = new ChatSearchState(() -> true);
		inactive.setQuery("missing");
		assertTrue(inactive.matches("message"));

		ChatSearchState disabled = new ChatSearchState(() -> false);
		disabled.activate();
		disabled.setQuery("missing");
		assertTrue(disabled.matches("message"));
	}
}
