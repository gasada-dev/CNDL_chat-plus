package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class ResponderScreenTest {
	@Test
	void refreshesWhenUnequalLastSeenMapsHaveTheSameHash() {
		Map<String, String> previous = Map.of("Alice", "Aa");
		Map<String, String> current = Map.of("Alice", "BB");

		assertTrue(ResponderScreen.friendStateChanged(Set.of(), Set.of(), current, previous));
	}
}
