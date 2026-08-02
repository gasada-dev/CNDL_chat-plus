package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FriendPresenceTracker {
	static final long ONLINE_NOTICE_MS = 4_000;
	static final long CONNECTION_WARMUP_MS = 30_000;
	static final long OFFLINE_CONFIRM_MS = 5_000;

	private final Set<String> previousOnline = new HashSet<>();
	private final Map<String, Long> offlineSince = new LinkedHashMap<>();
	private final Map<String, Long> onlineNotices = new LinkedHashMap<>();
	private boolean notificationsArmed;
	private Object activeConnection;
	private long notificationsEnabledAt;

	public FriendHudSnapshot update(ActiveTemplateSnapshot template, Set<String> onlinePlayerNames,
			Object connection, long now) {
		if (template == null || connection == null) {
			reset();
			return FriendHudSnapshot.empty();
		}
		if (activeConnection != connection) {
			reset();
			activeConnection = connection;
			notificationsEnabledAt = now + CONNECTION_WARMUP_MS;
		}

		Set<String> normalizedOnlinePlayers = new HashSet<>();
		onlinePlayerNames.forEach(name -> normalizedOnlinePlayers.add(name.toLowerCase(Locale.ROOT)));
		List<String> online = new ArrayList<>();
		Set<String> currentOnline = new HashSet<>();
		for (String friend : template.friends()) {
			String normalized = friend.toLowerCase(Locale.ROOT);
			if (normalizedOnlinePlayers.contains(normalized)) {
				online.add(friend);
				currentOnline.add(normalized);
			}
		}

		boolean friendCameOnline = false;
		if (!notificationsArmed && now >= notificationsEnabledAt) {
			notificationsArmed = true;
			for (String friend : template.friends()) {
				String normalized = friend.toLowerCase(Locale.ROOT);
				if (!currentOnline.contains(normalized)) {
					offlineSince.put(normalized, now);
				}
			}
		} else if (notificationsArmed) {
			for (String friend : template.friends()) {
				String normalized = friend.toLowerCase(Locale.ROOT);
				if (!currentOnline.contains(normalized)) {
					offlineSince.putIfAbsent(normalized, now);
					continue;
				}
				Long confirmedOfflineAt = offlineSince.remove(normalized);
				if (!previousOnline.contains(normalized) && confirmedOfflineAt != null
						&& now - confirmedOfflineAt >= OFFLINE_CONFIRM_MS) {
					onlineNotices.remove(friend);
					onlineNotices.put(friend, now + ONLINE_NOTICE_MS);
					friendCameOnline = true;
				}
			}
		}
		previousOnline.clear();
		previousOnline.addAll(currentOnline);
		onlineNotices.entrySet().removeIf(entry -> entry.getValue() <= now);
		if (!template.friendHudEnabled()) {
			onlineNotices.clear();
			return new FriendHudSnapshot(List.of(), List.of(), false, false);
		}
		return new FriendHudSnapshot(online, new ArrayList<>(onlineNotices.keySet()), true,
				friendCameOnline && template.friendSoundEnabled());
	}

	public void reset() {
		previousOnline.clear();
		offlineSince.clear();
		onlineNotices.clear();
		notificationsArmed = false;
		activeConnection = null;
		notificationsEnabledAt = 0L;
	}
}
