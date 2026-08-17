package ru.gasada.cndlchatplus;

public final class ChatChannelDetector {
	private final ActiveTemplateSnapshot template;
	private final DiscordMessageParser discordParser;

	public ChatChannelDetector(ActiveTemplateSnapshot template, CompiledParserSettings parsers) {
		this.template = template;
		this.discordParser = new DiscordMessageParser(parsers);
	}

	public ChatChannel detect(String content, String displayed) {
		String normalizedContent = ChatTextNormalizer.normalizeForMatching(content);
		String normalizedDisplayed = ChatTextNormalizer.normalizeForMatching(displayed);
		if (discordParser.parse(normalizedDisplayed).discordMessage()) {
			return ChatChannel.GLOBAL;
		}
		if (containsAnyMarker(normalizedDisplayed, template.privateMarkers())) {
			return ChatChannel.PRIVATE;
		}
		if (containsAnyMarker(normalizedDisplayed, template.clanMarkers())) {
			return ChatChannel.CLAN;
		}
		if (!template.globalPrefix().isBlank()
				&& normalizedContent.startsWith(ChatTextNormalizer.normalizeForMatching(template.globalPrefix()))) {
			return ChatChannel.GLOBAL;
		}
		if (normalizedDisplayed.contains("(!)")
				|| containsAnyMarker(normalizedDisplayed, template.globalMarkers())) {
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
