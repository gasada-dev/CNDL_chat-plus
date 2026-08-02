package ru.gasada.chatresponder;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class FriendLookupManager {
	private static final long COMMAND_DELAY_MS = 2_500;
	private static final long RESPONSE_TIMEOUT_MS = 7_000;
	private static final Pattern LAST_SEEN = Pattern.compile(
			"(?iu)Был\\s+(?:в\\s+сети|онлайн)\\s*:\\s*([^\\r\\n]+)");
	private static final Pattern INACTIVE = Pattern.compile(
			"(?iu)Неактивен\\s*:\\s*([^\\r\\n]+)");
	private static final Pattern LOOKUP_END = Pattern.compile("(?iu)Тип\\s+убийства\\s*:");
	private static final Pattern LOOKUP_OUTPUT = Pattern.compile(
			"(?iu)(?:информация\\s+об\\s+игроке|профиль\\s+игрока|был\\s+(?:в\\s+сети|онлайн)|"
					+ "последн(?:ий|яя)\\s+(?:вход|активность)|ранг\\s*:|(?:кпд|kdr)\\s*:|убийств\\s*:|"
					+ "нейтральных\\s*:|смертей\\s*:|дата\\s+вступления\\s*:|прошлые\\s+кланы\\s*:|"
					+ "неактивен\\s*:|тип\\s+убийства\\s*:|статус\\s*:|клан\\s*:)");
	private static final Pattern TIMESTAMP_ONLY = Pattern.compile("\\s*\\[\\d{1,2}:\\d{2}(?::\\d{2})?]\\s*");

	private final ResponderConfig config;
	private final Deque<String> queue = new ArrayDeque<>();
	private String pendingFriend;
	private String pendingLastSeen;
	private long pendingSince;
	private long nextCommandAt;

	public FriendLookupManager(ResponderConfig config) {
		this.config = config;
	}

	public void queueFriends(Collection<String> friends) {
		for (String friend : friends) {
			if (friend == null || friend.isBlank() || isAlreadyQueued(friend)) {
				continue;
			}
			queue.addLast(friend);
		}
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.getConnection() == null) {
			queue.clear();
			pendingFriend = null;
			pendingLastSeen = null;
			return;
		}

		long now = System.currentTimeMillis();
		if (pendingFriend != null && now - pendingSince < RESPONSE_TIMEOUT_MS) {
			return;
		}
		if (pendingFriend != null) {
			finishLookup();
		}
		if (queue.isEmpty() || now < nextCommandAt) {
			return;
		}

		pendingFriend = queue.removeFirst();
		pendingLastSeen = null;
		pendingSince = now;
		minecraft.getConnection().sendCommand("clan lookup " + pendingFriend);
	}

	public boolean shouldShowSystemMessage(Component message, boolean overlay) {
		if (overlay) {
			return true;
		}

		String text = message.getString();
		if (text.isBlank() || TIMESTAMP_ONLY.matcher(text).matches()) {
			return false;
		}

		Matcher matcher = LAST_SEEN.matcher(text);
		if (matcher.find()) {
			if (pendingFriend != null) {
				pendingLastSeen = matcher.group(1).trim();
			}
			return false;
		}

		Matcher inactive = INACTIVE.matcher(text);
		if (inactive.find()) {
			if (pendingFriend != null && pendingLastSeen == null) {
				String value = inactive.group(1).trim();
				pendingLastSeen = value.toLowerCase(Locale.ROOT).endsWith("назад")
						? value : value + " назад";
			}
			return false;
		}
		if (LOOKUP_END.matcher(text).find()) {
			if (pendingFriend != null) {
				finishLookup();
			}
			return false;
		}
		if (LOOKUP_OUTPUT.matcher(text).find()) {
			return false;
		}
		if (pendingFriend == null) {
			return true;
		}

		String normalized = text.toLowerCase(Locale.ROOT);
		return !normalized.contains(pendingFriend.toLowerCase(Locale.ROOT));
	}

	private void finishLookup() {
		if (pendingFriend != null && pendingLastSeen != null) {
			String configName = config.friends.stream()
					.filter(friend -> friend.equalsIgnoreCase(pendingFriend))
					.findFirst()
					.orElse(pendingFriend);
			config.friendLastSeen.put(configName, pendingLastSeen);
			ConfigManager.save(config);
		}
		pendingFriend = null;
		pendingLastSeen = null;
		nextCommandAt = System.currentTimeMillis() + COMMAND_DELAY_MS;
	}

	private boolean isAlreadyQueued(String friend) {
		String normalized = friend.toLowerCase(Locale.ROOT);
		return pendingFriend != null && pendingFriend.equalsIgnoreCase(friend)
				|| queue.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized));
	}
}
