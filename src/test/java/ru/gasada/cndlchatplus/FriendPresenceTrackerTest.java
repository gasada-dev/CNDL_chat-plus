package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

final class FriendPresenceTrackerTest {
	@Test
	void noticeRequiresWarmupAndConfirmedOfflineAndOnlineIntervals() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		VanillaBoxSnapshot snapshot = snapshot("Alice");
		Object connection = new Object();

		assertFalse(tracker.update(snapshot, Set.of("Alice"), connection, 0, true, true).playSound());
		assertFalse(tracker.update(snapshot, Set.of(), connection, 30_000, true, true).playSound());
		assertFalse(tracker.update(snapshot, Set.of(), connection, 34_999, true, true).playSound());
		assertFalse(tracker.update(snapshot, Set.of("ALICE"), connection, 35_000, true, true).playSound());
		assertFalse(tracker.update(snapshot, Set.of("Alice"), connection, 36_499, true, true).playSound());
		FriendHudSnapshot online = tracker.update(snapshot, Set.of("ALICE"), connection, 36_500, true, true);
		assertTrue(online.playSound());
		assertEquals(java.util.List.of("Alice"), online.onlineFriends());
		assertEquals(java.util.List.of("Alice"), online.notices());
		assertTrue(tracker.update(snapshot, Set.of("Alice"), connection, 40_499, true, true)
				.notices().contains("Alice"));
		assertTrue(tracker.update(snapshot, Set.of("Alice"), connection, 40_500, true, true)
				.notices().isEmpty());
	}

	@Test
	void returningOfflineDuringOnlineConfirmationCancelsNotice() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		VanillaBoxSnapshot snapshot = snapshot("Alice");
		Object connection = new Object();
		tracker.update(snapshot, Set.of(), connection, 0, true, true);
		tracker.update(snapshot, Set.of(), connection, 30_000, true, true);
		tracker.update(snapshot, Set.of("Alice"), connection, 35_000, true, true);

		assertFalse(tracker.update(snapshot, Set.of(), connection, 36_000, true, true).playSound());
		FriendHudSnapshot result = tracker.update(snapshot, Set.of(), connection, 36_500, true, true);

		assertFalse(result.playSound());
		assertTrue(result.notices().isEmpty());
	}

	@Test
	void newConnectionDoesNotProduceFalseOnlineNotice() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		VanillaBoxSnapshot snapshot = snapshot("Alice");
		Object first = new Object();
		tracker.update(snapshot, Set.of(), first, 0, true, true);
		tracker.update(snapshot, Set.of(), first, 30_000, true, true);
		tracker.update(snapshot, Set.of("Alice"), first, 35_000, true, true);
		tracker.update(snapshot, Set.of("Alice"), first, 36_500, true, true);

		FriendHudSnapshot afterReconnect = tracker.update(snapshot, Set.of("Alice"), new Object(), 37_000,
				true, true);
		assertFalse(afterReconnect.playSound());
		assertTrue(afterReconnect.notices().isEmpty());
	}

	@Test
	void disabledHudClearsNoticesButKeepsIndependentSound() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		VanillaBoxConfig source = VanillaBoxConfig.empty();
		source.friends.add("Alice");
		Object connection = new Object();
		VanillaBoxSnapshot snapshot = VanillaBoxSnapshot.from(source, 1);
		tracker.update(snapshot, Set.of(), connection, 0, false, true);
		tracker.update(snapshot, Set.of(), connection, 30_000, false, true);
		tracker.update(snapshot, Set.of("Alice"), connection, 35_000, false, true);
		FriendHudSnapshot result = tracker.update(snapshot, Set.of("Alice"), connection, 36_500,
				false, true);
		assertFalse(result.hudEnabled());
		assertTrue(result.playSound());
		assertTrue(result.notices().isEmpty());
	}

	@Test
	void disabledSoundKeepsHudNotice() {
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		VanillaBoxSnapshot snapshot = snapshot("Alice");
		Object connection = new Object();
		tracker.update(snapshot, Set.of(), connection, 0, true, false);
		tracker.update(snapshot, Set.of(), connection, 30_000, true, false);

		tracker.update(snapshot, Set.of("Alice"), connection, 35_000, true, false);
		FriendHudSnapshot result = tracker.update(snapshot, Set.of("Alice"), connection, 36_500,
				true, false);

		assertTrue(result.hudEnabled());
		assertFalse(result.playSound());
		assertEquals(java.util.List.of("Alice"), result.notices());
	}

	private static VanillaBoxSnapshot snapshot(String friend) {
		VanillaBoxConfig source = VanillaBoxConfig.empty();
		source.friends.add(friend);
		return VanillaBoxSnapshot.from(source, 1);
	}
}
