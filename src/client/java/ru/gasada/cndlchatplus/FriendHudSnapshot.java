package ru.gasada.cndlchatplus;

import java.util.List;

public record FriendHudSnapshot(List<String> onlineFriends, List<String> notices,
		boolean hudEnabled, boolean playSound) {
	public FriendHudSnapshot {
		onlineFriends = List.copyOf(onlineFriends);
		notices = List.copyOf(notices);
	}

	public static FriendHudSnapshot empty() {
		return new FriendHudSnapshot(List.of(), List.of(), false, false);
	}
}
