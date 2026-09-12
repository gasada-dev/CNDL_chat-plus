package ru.gasada.cndlchatplus;

import java.util.List;
import java.util.Map;

record VanillaBoxSnapshot(
		long generation,
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
		ParserSnapshot parsers,
		CompiledParserSettings compiledParsers,
		CompiledFilterSet compiledFilters) {

	static VanillaBoxSnapshot from(VanillaBoxConfig config, long generation) {
		VanillaBoxConfig snapshotConfig = config.deepCopy();
		snapshotConfig.sanitize();
		ParserSnapshot parsers = ParserSnapshot.from(snapshotConfig.parsers);
		CompiledParserSettings compiledParsers = CompiledParserSettings.compileValidated(parsers);
		CompiledFilterSet compiledFilters = CompiledFilterSet.compile(snapshotConfig.mutedWords);
		return new VanillaBoxSnapshot(
				generation,
				snapshotConfig.globalPrefix,
				snapshotConfig.globalMarkers,
				snapshotConfig.clanMarkers,
				snapshotConfig.privateMarkers,
				List.copyOf(snapshotConfig.mutedWords),
				List.copyOf(snapshotConfig.discordMutedPlayers),
				List.copyOf(snapshotConfig.mutedMinecraftPlayers),
				List.copyOf(snapshotConfig.friends),
				Map.copyOf(snapshotConfig.friendLastSeen),
				snapshotConfig.teleportAutoAcceptMode,
				List.copyOf(snapshotConfig.teleportAutoAcceptFriends),
				CommandSnapshot.from(snapshotConfig.commands),
				parsers,
				compiledParsers,
				compiledFilters);
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
