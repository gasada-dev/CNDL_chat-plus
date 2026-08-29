package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResponderConfig {
	public static final int MIN_CHAT_HISTORY_LIMIT = 100;
	public static final int MAX_CHAT_HISTORY_LIMIT = 16384;
	public static final int DEFAULT_CHAT_HISTORY_LIMIT = 1000;

	public boolean enabled = true;
	public Boolean discordChatEnabled = true;
	public List<String> discordMutedPlayers = new ArrayList<>();
	public List<String> mutedWords = new ArrayList<>();
	public List<String> friends = new ArrayList<>();
	public Map<String, String> friendLastSeen = new LinkedHashMap<>();
	public Boolean friendHudEnabled = true;
	public TeleportAutoAcceptMode teleportAutoAcceptMode = TeleportAutoAcceptMode.OFF;
	public List<String> teleportAutoAcceptFriends = new ArrayList<>();
	public List<PeriodicMessageConfig> periodicMessages = new ArrayList<>();
	public Boolean periodicEnabled;
	public String periodicMessage;
	public Integer periodicIntervalMinutes;
	public List<ReplyRule> rules = new ArrayList<>();
	public String globalPrefix = "!";
	public String clanReplyPrefix = "/.";
	public String privateReplyCommand = "/r";
	public String globalMarkers = "(!),[g],[global],[глобальный],глобальный чат";
	public String clanMarkers = "(клан),<клан>,〈клан〉,‹клан›";
	public String privateMarkers = "[pm],[лс],личное сообщение,шепчет,->,→";
	public Boolean chatHistoryEnabled = true;
	public Boolean chatHistoryPersist = true;
	public Integer chatHistoryLimit = DEFAULT_CHAT_HISTORY_LIMIT;
	public Boolean chatTabsEnabled = true;
	public Boolean chatTimestampsEnabled = true;
	public Boolean chatSearchEnabled = true;
	public Boolean chatContextMenuEnabled = true;

	public static ResponderConfig defaults() {
		ResponderConfig config = new ResponderConfig();
		config.rules.add(new ReplyRule("Всем привет", "привет", ChatChannel.AUTO));
		config.periodicMessages.add(new PeriodicMessageConfig());
		return config;
	}

	public void sanitize() {
		if (discordChatEnabled == null) {
			discordChatEnabled = true;
		}
		if (discordMutedPlayers == null) {
			discordMutedPlayers = new ArrayList<>();
		}
		if (mutedWords == null) {
			mutedWords = new ArrayList<>();
		}
		if (friends == null) {
			friends = new ArrayList<>();
		}
		if (friendLastSeen == null) {
			friendLastSeen = new LinkedHashMap<>();
		}
		if (friendHudEnabled == null) {
			friendHudEnabled = true;
		}
		if (teleportAutoAcceptMode == null) {
			teleportAutoAcceptMode = TeleportAutoAcceptMode.OFF;
		}
		if (teleportAutoAcceptFriends == null) {
			teleportAutoAcceptFriends = new ArrayList<>();
		}
		discordMutedPlayers.removeIf(value -> value == null || value.isBlank());
		mutedWords.removeIf(value -> value == null || value.isBlank());
		discordMutedPlayers = distinctIgnoringCase(discordMutedPlayers);
		mutedWords = distinctIgnoringCase(mutedWords);
		friends.removeIf(value -> value == null || value.isBlank());
		friends = distinctIgnoringCase(friends);
		teleportAutoAcceptFriends.removeIf(value -> value == null || value.isBlank());
		teleportAutoAcceptFriends = distinctIgnoringCase(teleportAutoAcceptFriends);
		teleportAutoAcceptFriends.removeIf(selected -> friends.stream()
				.noneMatch(friend -> friend.equalsIgnoreCase(selected)));
		friendLastSeen.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
				|| entry.getValue() == null || entry.getValue().isBlank());
		if (globalPrefix == null) {
			globalPrefix = "!";
		}
		if (globalMarkers == null) {
			globalMarkers = "";
		}
		if (!containsMarker(globalMarkers, "(!)")) {
			globalMarkers = globalMarkers.isBlank() ? "(!)" : "(!)," + globalMarkers;
		}
		if (clanMarkers == null) {
			clanMarkers = "";
		}
		if (privateMarkers == null) {
			privateMarkers = "";
		}
		if (chatHistoryEnabled == null) {
			chatHistoryEnabled = true;
		}
		if (chatHistoryPersist == null) {
			chatHistoryPersist = true;
		}
		if (chatHistoryLimit == null) {
			chatHistoryLimit = DEFAULT_CHAT_HISTORY_LIMIT;
		}
		chatHistoryLimit = Math.clamp(chatHistoryLimit, MIN_CHAT_HISTORY_LIMIT, MAX_CHAT_HISTORY_LIMIT);
		if (chatTabsEnabled == null) {
			chatTabsEnabled = true;
		}
		if (chatTimestampsEnabled == null) {
			chatTimestampsEnabled = true;
		}
		if (chatSearchEnabled == null) {
			chatSearchEnabled = true;
		}
		if (chatContextMenuEnabled == null) {
			chatContextMenuEnabled = true;
		}
	}

	private static boolean containsMarker(String markers, String expected) {
		for (String marker : markers.split(",")) {
			if (marker.trim().equalsIgnoreCase(expected)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> distinctIgnoringCase(List<String> values) {
		List<String> result = new ArrayList<>();
		for (String value : values) {
			String trimmed = value.trim();
			if (result.stream().noneMatch(existing -> existing.equalsIgnoreCase(trimmed))) {
				result.add(trimmed);
			}
		}
		return result;
	}
}
