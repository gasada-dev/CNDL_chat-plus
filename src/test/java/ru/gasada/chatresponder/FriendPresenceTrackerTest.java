package ru.gasada.chatresponder;

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

		assertFalse(tracker.update(template, Set.of("Alice"), connection, 0).playSound());
		assertFalse(tracker.update(template, Set.of(), connection, 30_000).playSound());
		assertFalse(tracker.update(template, Set.of(), connection, 34_999).playSound());
		FriendHudSnapshot online = tracker.update(template, Set.of("ALICE"), connection, 35_000);
		assertTrue(online.playSound());
		assertEquals(java.util.List.of("Alice"), online.onlineFriends());
		assertEquals(java.util.List.of("Alice"), online.notices());
		assertTrue(tracker.update(template, Set.of("Alice"), connection, 38_999).notices().contains("Alice"));
		assertTrue(tracker.update(template, Set.of("Alice"), connection, 39_000).notices().isEmpty());
	}

	@Test
	void newConnectionDoesNotProduceFalseOnlineNotice() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		ActiveTemplateSnapshot template = template("one", "Alice");
		Object first = new Object();
		tracker.update(template, Set.of(), first, 0);
		tracker.update(template, Set.of(), first, 30_000);
		tracker.update(template, Set.of("Alice"), first, 35_000);

		FriendHudSnapshot afterReconnect = tracker.update(template, Set.of("Alice"), new Object(), 36_000);
		assertFalse(afterReconnect.playSound());
		assertTrue(afterReconnect.notices().isEmpty());
	}

	@Test
	void disabledHudClearsNoticesAndSuppressesSound() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		ServerTemplate source = ServerTemplate.empty("one", "One");
		source.friends.add("Alice");
		source.friendHudEnabled = false;
		Object connection = new Object();
		ActiveTemplateSnapshot template = ActiveTemplateSnapshot.from(source, 1);
		tracker.update(template, Set.of(), connection, 0);
		tracker.update(template, Set.of(), connection, 30_000);
		FriendHudSnapshot result = tracker.update(template, Set.of("Alice"), connection, 35_000);
		assertFalse(result.hudEnabled());
		assertFalse(result.playSound());
		assertTrue(result.notices().isEmpty());
	}

	private static ActiveTemplateSnapshot template(String id, String friend) {
		ServerTemplate source = ServerTemplate.empty(id, id);
		source.friends.add(friend);
		return ActiveTemplateSnapshot.from(source, 1);
	}
}
