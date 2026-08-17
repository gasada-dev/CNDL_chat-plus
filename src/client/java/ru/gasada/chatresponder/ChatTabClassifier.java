package ru.gasada.chatresponder;

public final class ChatTabClassifier {
	// как hardcoded "(!)" в ChatChannelDetector: распространённые маркеры локального чата
	private static final String LOCAL_MARKERS = "(л),[л],〈л〉,‹л›";

	private final ServerTemplateRuntime templateRuntime;

	public ChatTabClassifier(ServerTemplateRuntime templateRuntime) {
		this.templateRuntime = templateRuntime;
	}

	public ChatTab classify(String displayed, boolean fromGame) {
		ActiveTemplateSnapshot template = templateRuntime.activeSnapshot().orElse(null);
		CompiledParserSettings parsers = templateRuntime.compiledParsers().orElse(null);
		String normalized = ChatTextNormalizer.normalizeForMatching(displayed);
		if (template != null && parsers != null) {
			if (new DiscordMessageParser(parsers).parse(normalized).discordMessage()) {
				return ChatTab.DISCORD;
			}
			// Маркеры важнее типа пакета: серверы могут слать чат системными сообщениями
			ChatTab tab = switch (new ChatChannelDetector(template, parsers).detect(displayed, displayed)) {
				case GLOBAL -> ChatTab.GLOBAL;
				case CLAN -> ChatTab.CLAN;
				case PRIVATE -> ChatTab.PRIVATE;
				default -> null;
			};
			if (tab != null) {
				return tab;
			}
		}
		for (String marker : LOCAL_MARKERS.split(",")) {
			if (normalized.contains(marker)) {
				return ChatTab.LOCAL;
			}
		}
		return fromGame ? ChatTab.SYSTEM : ChatTab.LOCAL;
	}
}
