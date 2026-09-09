package ru.gasada.cndlchatplus;

import java.util.List;
import java.util.Map;

public record ActiveTemplateSnapshot(
		long generation,
		String id,
		String name,
		String globalPrefix,
		String globalMarkers,
		String clanMarkers,
		String privateMarkers,
		List<String> mutedWords,
		List<String> discordMutedPlayers,
		List<String> mutedMinecraftPlayers,
		List<String> friends,
		Map<String, String> friendLastSeen,
		TeleportAutoAcceptMode teleportAutoAcceptMode,
		List<String> teleportAutoAcceptFriends,
		CommandSnapshot commands,
		ParserSnapshot parsers) {

	public static ActiveTemplateSnapshot from(ServerTemplate template, long generation) {
		ServerTemplate safe = template.deepCopy(template.id, template.name);
		return new ActiveTemplateSnapshot(
				generation,
				safe.id,
				safe.name,
				safe.globalPrefix,
				safe.globalMarkers,
				safe.clanMarkers,
				safe.privateMarkers,
				List.copyOf(safe.mutedWords),
				List.copyOf(safe.discordMutedPlayers),
				List.copyOf(safe.mutedMinecraftPlayers),
				List.copyOf(safe.friends),
				Map.copyOf(safe.friendLastSeen),
				safe.teleportAutoAcceptMode,
				List.copyOf(safe.teleportAutoAcceptFriends),
				CommandSnapshot.from(safe.commands),
				ParserSnapshot.from(safe.parsers));
	}

	public record CommandSnapshot(String ignorePlayer, String lookupFriend, String privateMessage,
			String pay, String call, String mail, String acceptTeleport,
			String protectionAdd, String protectionRemove, String traderTrustedAdd,
			String traderTrustedRemove, String claimFly, String enderChest,
			String marryKiss, String marryHome, String marryTp) {
		private static CommandSnapshot from(ServerCommandSettings settings) {
			return new CommandSnapshot(settings.ignorePlayer, settings.lookupFriend, settings.privateMessage,
					settings.pay, settings.call, settings.mail, settings.acceptTeleport,
					settings.protectionAdd, settings.protectionRemove, settings.traderTrustedAdd,
					settings.traderTrustedRemove, settings.claimFly, settings.enderChest,
					settings.marryKiss, settings.marryHome, settings.marryTp);
		}
	}

	public record ParserSnapshot(String discordMarkerPattern, String discordNamePattern,
			String lastSeenPattern, String inactivePattern, String lookupEndPattern,
			String lookupOutputPattern, String timestampOnlyPattern, List<String> replyCandidateSeparators,
			Map<String, String> playerInfoPatterns, String teleportRequestPattern) {
		private static ParserSnapshot from(ParserSettings settings) {
			return new ParserSnapshot(settings.discordMarkerPattern, settings.discordNamePattern,
					settings.lastSeenPattern, settings.inactivePattern, settings.lookupEndPattern,
					settings.lookupOutputPattern, settings.timestampOnlyPattern,
					List.copyOf(settings.replyCandidateSeparators), Map.copyOf(settings.playerInfoPatterns),
					settings.teleportRequestPattern);
		}
	}

}
