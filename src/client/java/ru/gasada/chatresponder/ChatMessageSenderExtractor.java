package ru.gasada.chatresponder;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatMessageSenderExtractor {
	private static final Pattern TIMESTAMP = Pattern.compile("^\\[\\d{2}:\\d{2}]\\s+");
	private static final Pattern PLAYER = Pattern.compile("[A-Za-z0-9_]{1,16}");

	public Optional<Sender> extract(String displayed, CompiledParserSettings parsers) {
		if (displayed == null || parsers == null) {
			return Optional.empty();
		}
		String text = TIMESTAMP.matcher(displayed).replaceFirst("");
		DiscordMessageParser.DiscordMessageInfo discord = new DiscordMessageParser(parsers).parse(text);
		if (discord.discordMessage()) {
			return discord.sender() == null || discord.sender().isBlank()
					? Optional.empty() : Optional.of(new Sender(discord.sender(), true));
		}
		int separatorIndex = -1;
		for (String separator : parsers.replyCandidateSeparators()) {
			if (separator != null && !separator.isEmpty()) {
				separatorIndex = Math.max(separatorIndex, text.lastIndexOf(separator));
			}
		}
		if (separatorIndex < 0) {
			return Optional.empty();
		}
		Matcher names = PLAYER.matcher(text.substring(0, separatorIndex));
		String sender = null;
		while (names.find()) {
			sender = names.group();
		}
		return sender != null && PlayerNameValidator.validate(sender).valid()
				? Optional.of(new Sender(sender, false)) : Optional.empty();
	}

	public record Sender(String name, boolean discord) {
	}
}
