package ru.gasada.chatresponder;

public final class ChatVisibilityFilter {
	private final ServerTemplateRuntime templateRuntime;

	public ChatVisibilityFilter(ServerTemplateRuntime templateRuntime) {
		this.templateRuntime = templateRuntime;
	}

	public VisibilityDecision decide(String text) {
		return decide(text, null);
	}

	public VisibilityDecision decide(String text, String minecraftSender) {
		ActiveTemplateSnapshot template = templateRuntime.activeSnapshot().orElse(null);
		CompiledParserSettings parsers = templateRuntime.compiledParsers().orElse(null);
		CompiledFilterSet filters = templateRuntime.compiledFilters().orElse(null);
		if (template == null || parsers == null || filters == null) {
			return VisibilityDecision.allow();
		}

		if (minecraftSender != null && containsIgnoringCase(template.mutedMinecraftPlayers(), minecraftSender)) {
			return VisibilityDecision.hidden(FilterReason.MINECRAFT_PLAYER_MUTED, minecraftSender);
		}

		DiscordMessageParser.DiscordMessageInfo discord = new DiscordMessageParser(parsers).parse(text);
		if (discord.discordMessage() && !template.discordChatEnabled()) {
			return VisibilityDecision.hidden(FilterReason.DISCORD_DISABLED, null);
		}
		if (discord.discordMessage() && discord.sender() != null
				&& containsIgnoringCase(template.discordMutedPlayers(), discord.sender())) {
			return VisibilityDecision.hidden(FilterReason.DISCORD_USER_MUTED, discord.sender());
		}

		return filters.firstMutedWord(text)
				.map(value -> VisibilityDecision.hidden(FilterReason.MUTED_WORD, value))
				.orElseGet(VisibilityDecision::allow);
	}

	private static boolean containsIgnoringCase(Iterable<String> values, String expected) {
		for (String value : values) {
			if (value.equalsIgnoreCase(expected)) {
				return true;
			}
		}
		return false;
	}
}
