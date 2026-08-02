package ru.gasada.chatresponder;

import java.util.List;
import java.util.Map;

public record ActiveTemplateSnapshot(
		long generation,
		String id,
		String name,
		boolean responderEnabled,
		List<RuleSnapshot> rules,
		String globalPrefix,
		String clanReplyPrefix,
		String privateReplyCommand,
		String globalMarkers,
		String clanMarkers,
		String privateMarkers,
		List<String> mutedWords,
		boolean discordChatEnabled,
		List<String> discordMutedPlayers,
		List<String> mutedMinecraftPlayers,
		List<String> friends,
		Map<String, String> friendLastSeen,
		boolean friendHudEnabled,
		boolean friendSoundEnabled,
		List<PeriodicSnapshot> periodicMessages,
		CommandSnapshot commands,
		ParserSnapshot parsers) {

	public static ActiveTemplateSnapshot from(ServerTemplate template, long generation) {
		ServerTemplate safe = template.deepCopy(template.id, template.name);
		return new ActiveTemplateSnapshot(
				generation,
				safe.id,
				safe.name,
				safe.responderEnabled,
				safe.rules.stream().map(RuleSnapshot::from).toList(),
				safe.globalPrefix,
				safe.clanReplyPrefix,
				safe.privateReplyCommand,
				safe.globalMarkers,
				safe.clanMarkers,
				safe.privateMarkers,
				List.copyOf(safe.mutedWords),
				safe.discordChatEnabled,
				List.copyOf(safe.discordMutedPlayers),
				List.copyOf(safe.mutedMinecraftPlayers),
				List.copyOf(safe.friends),
				Map.copyOf(safe.friendLastSeen),
				safe.friendHudEnabled,
				safe.friendSoundEnabled,
				safe.periodicMessages.stream().map(PeriodicSnapshot::from).toList(),
				CommandSnapshot.from(safe.commands),
				ParserSnapshot.from(safe.parsers));
	}

	public record RuleSnapshot(boolean enabled, String trigger, String response, ChatChannel channel) {
		private static RuleSnapshot from(ReplyRule rule) {
			return new RuleSnapshot(rule.enabled, rule.trigger, rule.response, rule.channel);
		}
	}

	public record PeriodicSnapshot(boolean enabled, String message, int intervalMinutes) {
		private static PeriodicSnapshot from(PeriodicMessageConfig config) {
			return new PeriodicSnapshot(config.enabled, config.message, config.intervalMinutes);
		}
	}

	public record CommandSnapshot(String ignorePlayer, String lookupFriend, String privateMessage,
			String pay, String call, String mail) {
		private static CommandSnapshot from(ServerCommandSettings settings) {
			return new CommandSnapshot(settings.ignorePlayer, settings.lookupFriend, settings.privateMessage,
					settings.pay, settings.call, settings.mail);
		}
	}

	public record ParserSnapshot(String discordMarkerPattern, String discordNamePattern,
			String lastSeenPattern, String inactivePattern, String lookupEndPattern,
			String lookupOutputPattern, String timestampOnlyPattern, List<String> replyCandidateSeparators) {
		private static ParserSnapshot from(ParserSettings settings) {
			return new ParserSnapshot(settings.discordMarkerPattern, settings.discordNamePattern,
					settings.lastSeenPattern, settings.inactivePattern, settings.lookupEndPattern,
					settings.lookupOutputPattern, settings.timestampOnlyPattern,
					List.copyOf(settings.replyCandidateSeparators));
		}
	}
}
