package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class FriendListSorterTest {
	@Test
	void putsOnlineFirstAndSortsOfflineByMostRecentLogin() {
		List<String> sorted = FriendListSorter.sort(
				List.of("Old", "OnlineB", "Recent", "Unknown", "OnlineA", "Yesterday"),
				Set.of("onlinea", "ONLINEB"),
				Map.of(
						"Old", "01.08.2026 18:42",
						"Recent", "2 часа назад",
						"Yesterday", "вчера в 21:15"),
				LocalDateTime.of(2026, 8, 31, 12, 0));

		assertEquals(List.of("OnlineB", "OnlineA", "Recent", "Yesterday", "Old", "Unknown"), sorted);
	}

	@Test
	void sortsTimeFirstSlashDateFormat() {
		List<String> sorted = FriendListSorter.sort(
				List.of("Aug28", "Aug30Late", "Aug18", "Aug30Early", "Aug31"), Set.of(),
				Map.of(
						"Aug28", "21:47 - 28/08/2026",
						"Aug30Late", "23:25 - 30/08/2026",
						"Aug18", "20:35 - 18/08/2026",
						"Aug30Early", "12:49 - 30/08/2026",
						"Aug31", "04:07 - 31/08/2026"),
				LocalDateTime.of(2026, 8, 31, 12, 0));

		assertEquals(List.of("Aug31", "Aug30Late", "Aug30Early", "Aug28", "Aug18"), sorted);
	}
}
