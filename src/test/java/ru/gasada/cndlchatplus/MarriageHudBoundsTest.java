package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class MarriageHudBoundsTest {
	@Test
	void positionsFivePixelsAboveBottomWithoutVisibleFriends() {
		MarriageHud.Bounds bounds = MarriageHud.bounds(320, 200, 70,
				FriendsHud.occupiedHeight(FriendHudSnapshot.empty()));

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
		int occupied = FriendsHud.occupiedHeight(friends);
		MarriageHud.Bounds marriage = MarriageHud.bounds(320, 200, 70, occupied);
		int friendsHeight = 8 + 3 * 11;
		int friendsTop = 200 - friendsHeight - 5;

		assertEquals(friendsHeight + 5, occupied);
		assertEquals(friendsTop - 5, marriage.y() + marriage.height());
	}

	@Test
	void hiddenFriendsOccupyNoHeight() {
		FriendHudSnapshot disabled = new FriendHudSnapshot(List.of("Alice"), List.of(), false, false);
		assertEquals(0, FriendsHud.occupiedHeight(disabled));
	}

	@Test
	void includesVisibleFriendNoticesInOccupiedHeight() {
		FriendHudSnapshot friends = new FriendHudSnapshot(
				List.of("Alice"), List.of("Alice", "Bob"), true, false);
		assertEquals(8 + 2 * 11 + 10 + 2 * 20, FriendsHud.occupiedHeight(friends));
	}
}
