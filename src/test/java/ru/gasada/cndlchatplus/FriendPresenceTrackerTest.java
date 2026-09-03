package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

final class FriendPresenceTrackerTest {
	@Test
	void noticeRequiresWarmupAndConfirmedOfflineInterval() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		ActiveTemplateSnapshot template = template("one", "Alice");
		Object connection = new Object();

		assertFalse(tracker.update(template, Set.of("Alice"), connection, 0, true, true).playSound());
		assertFalse(tracker.update(template, Set.of(), connection, 30_000, true, true).playSound());
		assertFalse(tracker.update(template, Set.of(), connection, 34_999, true, true).playSound());
		FriendHudSnapshot online = tracker.update(template, Set.of("ALICE"), connection, 35_000, true, true);
		assertTrue(online.playSound());
		assertEquals(java.util.List.of("Alice"), online.onlineFriends());
		assertEquals(java.util.List.of("Alice"), online.notices());
		assertTrue(tracker.update(template, Set.of("Alice"), connection, 38_999, true, true)
				.notices().contains("Alice"));
		assertTrue(tracker.update(template, Set.of("Alice"), connection, 39_000, true, true)
				.notices().isEmpty());
	}

	@Test
	void newConnectionDoesNotProduceFalseOnlineNotice() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		ActiveTemplateSnapshot template = template("one", "Alice");
		Object first = new Object();
		tracker.update(template, Set.of(), first, 0, true, true);
		tracker.update(template, Set.of(), first, 30_000, true, true);
		tracker.update(template, Set.of("Alice"), first, 35_000, true, true);

		FriendHudSnapshot afterReconnect = tracker.update(template, Set.of("Alice"), new Object(), 36_000,
				true, true);
		assertFalse(afterReconnect.playSound());
		assertTrue(afterReconnect.notices().isEmpty());
	}

	@Test
	void disabledHudClearsNoticesButKeepsIndependentSound() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		ServerTemplate source = ServerTemplate.empty("one", "One");
		source.friends.add("Alice");
		Object connection = new Object();
		ActiveTemplateSnapshot template = ActiveTemplateSnapshot.from(source, 1);
		tracker.update(template, Set.of(), connection, 0, false, true);
		tracker.update(template, Set.of(), connection, 30_000, false, true);
		FriendHudSnapshot result = tracker.update(template, Set.of("Alice"), connection, 35_000,
				false, true);
		assertFalse(result.hudEnabled());
		assertTrue(result.playSound());
		assertTrue(result.notices().isEmpty());
	}

	@Test
	void disabledSoundKeepsHudNotice() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		ActiveTemplateSnapshot template = template("one", "Alice");
		Object connection = new Object();
		tracker.update(template, Set.of(), connection, 0, true, false);
		tracker.update(template, Set.of(), connection, 30_000, true, false);

		FriendHudSnapshot result = tracker.update(template, Set.of("Alice"), connection, 35_000,
				true, false);

		assertTrue(result.hudEnabled());
		assertFalse(result.playSound());
		assertEquals(java.util.List.of("Alice"), result.notices());
	}

	private static ActiveTemplateSnapshot template(String id, String friend) {
		ServerTemplate source = ServerTemplate.empty(id, id);
		source.friends.add(friend);
		return ActiveTemplateSnapshot.from(source, 1);
	}
}
