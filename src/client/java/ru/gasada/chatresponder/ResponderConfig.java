package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ResponderConfig {
	public boolean enabled = true;
	public Boolean discordChatEnabled = true;
	public List<String> discordMutedPlayers = new ArrayList<>();
	public List<String> mutedWords = new ArrayList<>();
	public List<String> friends = new ArrayList<>();
	public Map<String, String> friendLastSeen = new LinkedHashMap<>();
	public Boolean friendHudEnabled = true;
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
		discordMutedPlayers.removeIf(value -> value == null || value.isBlank());
		mutedWords.removeIf(value -> value == null || value.isBlank());
		discordMutedPlayers = distinctIgnoringCase(discordMutedPlayers);
		mutedWords = distinctIgnoringCase(mutedWords);
		friends.removeIf(value -> value == null || value.isBlank());
		friends = distinctIgnoringCase(friends);
		friendLastSeen.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
				|| entry.getValue() == null || entry.getValue().isBlank());
		if (rules == null) {
			rules = new ArrayList<>();
		}
		if (periodicMessages == null) {
			periodicMessages = new ArrayList<>();
		}
		if (periodicMessages.isEmpty() && periodicEnabled != null) {
			periodicMessages.add(new PeriodicMessageConfig(Boolean.TRUE.equals(periodicEnabled),
					periodicMessage == null ? "" : periodicMessage,
					periodicIntervalMinutes == null ? 5 : periodicIntervalMinutes));
		}
		periodicEnabled = null;
		periodicMessage = null;
		periodicIntervalMinutes = null;
		periodicMessages.removeIf(message -> message == null);
		if (periodicMessages.isEmpty()) {
			periodicMessages.add(new PeriodicMessageConfig());
		}
		if (periodicMessages.size() > 3) {
			periodicMessages = new ArrayList<>(periodicMessages.subList(0, 3));
		}
		for (PeriodicMessageConfig message : periodicMessages) {
			if (message.message == null) {
				message.message = "";
			}
			if (message.intervalMinutes < 1) {
				message.intervalMinutes = 5;
			}
		}
		if (globalPrefix == null) {
			globalPrefix = "!";
		}
		if (clanReplyPrefix == null) {
			clanReplyPrefix = "/.";
		}
		if (privateReplyCommand == null) {
			privateReplyCommand = "/r";
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

		rules.removeIf(rule -> rule == null);
		for (ReplyRule rule : rules) {
			if (rule.trigger == null) {
				rule.trigger = "";
			}
			if (rule.response == null) {
				rule.response = "";
			}
			if (rule.channel == null) {
				rule.channel = ChatChannel.AUTO;
			}
		}

		if (hasOldDefaultRules()) {
			rules.clear();
			rules.add(new ReplyRule("Всем привет", "привет", ChatChannel.AUTO));
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

	private boolean hasOldDefaultRules() {
		if (rules.size() != 2) {
			return false;
		}
		ReplyRule first = rules.get(0);
		ReplyRule second = rules.get(1);
		return first.trigger.equalsIgnoreCase("амадо где гасада")
				&& second.trigger.equalsIgnoreCase("гасада где амадо")
				&& first.response.equalsIgnoreCase("тих тих")
				&& second.response.equalsIgnoreCase("тих тих");
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
