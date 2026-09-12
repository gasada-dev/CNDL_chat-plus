package ru.gasada.cndlchatplus;

public final class ChatChannelDetector {
	private final VanillaBoxSnapshot snapshot;
	private final DiscordMessageParser discordParser;

	public ChatChannelDetector(VanillaBoxSnapshot snapshot, CompiledParserSettings parsers) {
		this.snapshot = snapshot;
		this.discordParser = new DiscordMessageParser(parsers);
	}

	public ChatChannel detect(String content, String displayed) {
		String normalizedContent = ChatTextNormalizer.normalizeForMatching(content);
		String normalizedDisplayed = ChatTextNormalizer.normalizeForMatching(displayed);
		if (discordParser.parse(normalizedDisplayed).discordMessage()) {
			return ChatChannel.GLOBAL;
		}
		if (containsAnyMarker(normalizedDisplayed, snapshot.privateMarkers())) {
			return ChatChannel.PRIVATE;
		}
		if (containsAnyMarker(normalizedDisplayed, snapshot.clanMarkers())) {
			return ChatChannel.CLAN;
		}
		if (!snapshot.globalPrefix().isBlank()
				&& normalizedContent.startsWith(ChatTextNormalizer.normalizeForMatching(snapshot.globalPrefix()))) {
			return ChatChannel.GLOBAL;
		}
		if (normalizedDisplayed.contains("(!)")
				|| containsAnyMarker(normalizedDisplayed, snapshot.globalMarkers())) {
			return ChatChannel.GLOBAL;
		}
		return ChatChannel.LOCAL;
	}

	private static boolean containsAnyMarker(String text, String commaSeparatedMarkers) {
		for (String marker : commaSeparatedMarkers.split(",")) {
			String normalizedMarker = ChatTextNormalizer.normalizeForMatching(marker);
			if (!normalizedMarker.isEmpty() && text.contains(normalizedMarker)) {
				return true;
			}
		}
		return false;
	}
}
