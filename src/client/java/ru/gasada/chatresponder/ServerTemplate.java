package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerTemplate {
	public String id;
	public String name;
	public boolean responderEnabled = true;
	public List<ReplyRule> rules = new ArrayList<>();
	public String globalPrefix = "";
	public String clanReplyPrefix = "";
	public String privateReplyCommand = "";
	public String globalMarkers = "";
	public String clanMarkers = "";
	public String privateMarkers = "";
	public List<String> mutedWords = new ArrayList<>();
	public boolean discordChatEnabled = true;
	public List<String> discordMutedPlayers = new ArrayList<>();
	public List<String> mutedMinecraftPlayers = new ArrayList<>();
	public List<String> friends = new ArrayList<>();
	public Map<String, String> friendLastSeen = new LinkedHashMap<>();
	public boolean friendHudEnabled = true;
	public boolean friendSoundEnabled = true;
	public List<PeriodicMessageConfig> periodicMessages = new ArrayList<>();
	public ServerCommandSettings commands = new ServerCommandSettings();
	public ParserSettings parsers = new ParserSettings();
	public PlayerInfoSettings playerInfo = new PlayerInfoSettings();

	public static ServerTemplate empty(String id, String name) {
		ServerTemplate template = new ServerTemplate();
		template.id = id;
		template.name = name;
		return template;
	}

	public void sanitize() {
		if (rules == null) rules = new ArrayList<>();
		rules.removeIf(rule -> rule == null);
		for (ReplyRule rule : rules) {
			if (rule.trigger == null) rule.trigger = "";
			if (rule.response == null) rule.response = "";
			if (rule.channel == null) rule.channel = ChatChannel.AUTO;
		}
		globalPrefix = safe(globalPrefix);
		clanReplyPrefix = safe(clanReplyPrefix);
		privateReplyCommand = safe(privateReplyCommand);
		globalMarkers = safe(globalMarkers);
		clanMarkers = safe(clanMarkers);
		privateMarkers = safe(privateMarkers);
		mutedWords = sanitizeStrings(mutedWords);
		discordMutedPlayers = sanitizeStrings(discordMutedPlayers);
		mutedMinecraftPlayers = sanitizeStrings(mutedMinecraftPlayers);
		friends = sanitizeStrings(friends);
		if (friendLastSeen == null) friendLastSeen = new LinkedHashMap<>();
		friendLastSeen.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
		if (periodicMessages == null) periodicMessages = new ArrayList<>();
		periodicMessages.removeIf(value -> value == null);
		if (periodicMessages.size() > PeriodicMessageConfig.MAX_PERIODIC_MESSAGES) {
			periodicMessages = new ArrayList<>(periodicMessages.subList(
					0, PeriodicMessageConfig.MAX_PERIODIC_MESSAGES));
		}
		for (PeriodicMessageConfig value : periodicMessages) {
			if (value.message == null) value.message = "";
		}
		if (commands == null) commands = new ServerCommandSettings();
		if (parsers == null) parsers = new ParserSettings();
		if (playerInfo == null) playerInfo = new PlayerInfoSettings();
		if (playerInfo.provider == null) playerInfo.provider = PlayerInfoProvider.NONE;
		if (parsers.replyCandidateSeparators == null) parsers.replyCandidateSeparators = new ArrayList<>();
		parsers.replyCandidateSeparators.removeIf(value -> value == null);
		if (parsers.playerInfoPatterns == null) parsers.playerInfoPatterns = new LinkedHashMap<>();
		parsers.playerInfoPatterns.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
				|| entry.getValue() == null || entry.getValue().isBlank());
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static List<String> sanitizeStrings(List<String> source) {
		List<String> result = new ArrayList<>(source == null ? List.of() : source);
		result.removeIf(value -> value == null);
		return result;
	}

	public ServerTemplate deepCopy(String newId, String newName) {
		ServerTemplate copy = new ServerTemplate();
		copy.id = newId;
		copy.name = newName;
		copy.responderEnabled = responderEnabled;
		copy.rules = copyRules(rules);
		copy.globalPrefix = globalPrefix;
		copy.clanReplyPrefix = clanReplyPrefix;
		copy.privateReplyCommand = privateReplyCommand;
		copy.globalMarkers = globalMarkers;
		copy.clanMarkers = clanMarkers;
		copy.privateMarkers = privateMarkers;
		copy.mutedWords = copyStrings(mutedWords);
		copy.discordChatEnabled = discordChatEnabled;
		copy.discordMutedPlayers = copyStrings(discordMutedPlayers);
		copy.mutedMinecraftPlayers = copyStrings(mutedMinecraftPlayers);
		copy.friends = copyStrings(friends);
		copy.friendLastSeen = new LinkedHashMap<>(friendLastSeen == null ? Map.of() : friendLastSeen);
		copy.friendHudEnabled = friendHudEnabled;
		copy.friendSoundEnabled = friendSoundEnabled;
		copy.periodicMessages = copyPeriodic(periodicMessages);
		copy.commands = commands == null ? new ServerCommandSettings() : commands.copy();
		copy.parsers = parsers == null ? new ParserSettings() : parsers.copy();
		copy.playerInfo = playerInfo == null ? new PlayerInfoSettings() : playerInfo.copy();
		return copy;
	}

	private static List<String> copyStrings(List<String> source) {
		return new ArrayList<>(source == null ? List.of() : source);
	}

	static List<ReplyRule> copyRules(List<ReplyRule> source) {
		List<ReplyRule> result = new ArrayList<>();
		if (source == null) {
			return result;
		}
		for (ReplyRule rule : source) {
			if (rule != null) {
				ReplyRule copiedRule = new ReplyRule(rule.trigger, rule.response, rule.channel);
				copiedRule.enabled = rule.enabled;
				result.add(copiedRule);
			}
		}
		return result;
	}

	private static List<PeriodicMessageConfig> copyPeriodic(List<PeriodicMessageConfig> source) {
		List<PeriodicMessageConfig> result = new ArrayList<>();
		if (source == null) {
			return result;
		}
		for (PeriodicMessageConfig entry : source) {
			if (entry != null) {
				result.add(new PeriodicMessageConfig(entry.enabled, entry.message, entry.intervalMinutes));
			}
		}
		return result;
	}
}
