package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class MarriageHudBoundsTest {
	@Test
	void positionsFivePixelsAboveBottomWithoutVisibleFriends() {
		MarriageHud.Bounds bounds = MarriageHud.bounds(320, 200, 70,
				FriendsHud.occupiedHeight(FriendHudSnapshot.empty(), 15), 0);

		assertEquals(233, bounds.x());
		assertEquals(165, bounds.y());
		assertEquals(82, bounds.width());
		assertEquals(30, bounds.height());
		assertTrue(bounds.contains(233, 165));
		assertFalse(bounds.contains(320, 195));
	}

	@Test
	void positionsFivePixelsAboveVisibleFriends() {
		FriendHudSnapshot friends = new FriendHudSnapshot(List.of("Alice", "Bob"), List.of(), true, false);
		int occupied = FriendsHud.occupiedHeight(friends, 15);
		MarriageHud.Bounds marriage = MarriageHud.bounds(320, 200, 70, occupied, 96);
		int friendsHeight = 8 + 3 * 11;
		int friendsTop = 200 - friendsHeight - 5;

		assertEquals(friendsHeight + 5, occupied);
		assertEquals(96, marriage.width());
		assertEquals(219, marriage.x());
		assertEquals(friendsTop - 5, marriage.y() + marriage.height());
	}

	@Test
	void keepsContentWidthWhenItExceedsVisibleFriends() {
		MarriageHud.Bounds bounds = MarriageHud.bounds(320, 200, 100, 0, 96);

		assertEquals(112, bounds.width());
		assertEquals(203, bounds.x());
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 15})
	void hiddenFriendsOccupyNoHeight(int limit) {
		FriendHudSnapshot disabled = new FriendHudSnapshot(List.of("Alice"), List.of("Alice"), false, true);
		FriendHudSnapshot empty = new FriendHudSnapshot(List.of(), List.of("Alice"), true, false);
		assertEquals(0, FriendsHud.occupiedHeight(disabled, limit));
		assertEquals(0, FriendsHud.occupiedHeight(empty, limit));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 15})
	void includesVisibleFriendNoticesInOccupiedHeight(int limit) {
		FriendHudSnapshot friends = new FriendHudSnapshot(
				List.of("Alice"), List.of("Alice", "Bob"), true, false);
		assertEquals(8 + 2 * 11 + 10 + 2 * 20, FriendsHud.occupiedHeight(friends, limit));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 15})
	void capsVisibleRowsAndMarriageOffsetWithoutChangingTotalOnlineCount(int limit) {
		List<String> online = IntStream.rangeClosed(1, 20).mapToObj(index -> "Friend" + index).toList();
		FriendHudSnapshot friends = new FriendHudSnapshot(online, List.of(), true, false);
		int occupied = FriendsHud.occupiedHeight(friends, limit);
		MarriageHud.Bounds marriage = MarriageHud.bounds(320, 400, 70, occupied, 96);
		int friendsHeight = 8 + (limit + 1) * 11;

		assertEquals(online.subList(0, limit), FriendsHud.displayedOnlineFriends(friends, limit));
		assertEquals(online, friends.onlineFriends());
		assertEquals(20, friends.onlineFriends().size());
		assertEquals(friendsHeight + 5, occupied);
		assertEquals(400 - friendsHeight - 10, marriage.y() + marriage.height());
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 15})
	void keepsPresenceNoticesAndSoundForFriendsOutsideDisplayedRows(int limit) {
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.friends.addAll(IntStream.rangeClosed(1, 20).mapToObj(index -> "Friend" + index).toList());
		VanillaBoxSnapshot source = VanillaBoxSnapshot.from(config, 1);
		FriendPresenceTracker tracker = new FriendPresenceTracker();
		Object connection = new Object();
		Set<String> initiallyOnline = Set.copyOf(config.friends.subList(0, 19));
		Set<String> allOnline = Set.copyOf(config.friends);
		tracker.update(source, initiallyOnline, connection, 0, true, true);
		tracker.update(source, initiallyOnline, connection, 30_000, true, true);
		tracker.update(source, allOnline, connection, 35_000, true, true);
		FriendHudSnapshot friends = tracker.update(source, allOnline, connection, 36_500, true, true);

		assertEquals(config.friends.subList(0, limit), FriendsHud.displayedOnlineFriends(friends, limit));
		assertEquals(config.friends, friends.onlineFriends());
		assertEquals(List.of("Friend20"), friends.notices());
		assertTrue(friends.playSound());
		assertEquals(8 + (limit + 1) * 11 + 10 + 20, FriendsHud.occupiedHeight(friends, limit));
		assertFalse(tracker.update(source, allOnline, connection, 36_501, true, true).playSound());
		assertTrue(tracker.update(source, allOnline, connection, 40_500, true, true).notices().isEmpty());
	}
}
