package ru.gasada.cndlchatplus;

import java.util.function.BooleanSupplier;

public final class ChatVisibilityFilter {
	private final VanillaBoxRuntime runtime;
	private final BooleanSupplier discordChatEnabled;

	public ChatVisibilityFilter(VanillaBoxRuntime runtime) {
		this(runtime, () -> true);
	}

	public ChatVisibilityFilter(VanillaBoxRuntime runtime, BooleanSupplier discordChatEnabled) {
		this.runtime = runtime;
		this.discordChatEnabled = discordChatEnabled;
	}

	public VisibilityDecision decide(String text) {
		return decide(text, null);
	}

	public VisibilityDecision decide(String text, String minecraftSender) {
		VanillaBoxSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (snapshot == null) {
			return VisibilityDecision.allow();
		}

		if (minecraftSender != null && containsIgnoringCase(snapshot.mutedMinecraftPlayers(), minecraftSender)) {
			return VisibilityDecision.hidden(FilterReason.MINECRAFT_PLAYER_MUTED, minecraftSender);
		}

		DiscordMessageParser.DiscordMessageInfo discord = new DiscordMessageParser(snapshot.compiledParsers()).parse(text);
		if (discord.discordMessage() && !discordChatEnabled.getAsBoolean()) {
			return VisibilityDecision.hidden(FilterReason.DISCORD_DISABLED, null);
		}
		if (discord.discordMessage() && discord.sender() != null
				&& containsIgnoringCase(snapshot.discordMutedPlayers(), discord.sender())) {
			return VisibilityDecision.hidden(FilterReason.DISCORD_USER_MUTED, discord.sender());
		}

		return snapshot.compiledFilters().firstMutedWord(text)
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
