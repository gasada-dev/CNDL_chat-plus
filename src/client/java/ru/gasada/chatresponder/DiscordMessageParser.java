package ru.gasada.chatresponder;

import java.util.regex.Matcher;

public final class DiscordMessageParser {
	private final CompiledParserSettings settings;

	public DiscordMessageParser(CompiledParserSettings settings) {
		this.settings = settings;
	}

	public DiscordMessageInfo parse(String text) {
		if (text == null || settings == null || settings.discordMarker().isEmpty()) {
			return new DiscordMessageInfo(false, null);
		}
		Matcher marker = settings.discordMarker().get().matcher(text);
		if (!marker.find()) {
			return new DiscordMessageInfo(false, null);
		}
		return new DiscordMessageInfo(true, extractSender(text, marker.end()));
	}

	private String extractSender(String text, int markerEnd) {
		if (settings.discordName().isEmpty()) {
			return null;
		}
		int separator = text.indexOf('»', markerEnd);
		String authorPart = separator >= 0 ? text.substring(markerEnd, separator) : text.substring(markerEnd);
		Matcher names = settings.discordName().get().matcher(authorPart);
		if (separator < 0) {
			return names.find() ? names.group() : null;
		}
		String lastName = null;
		while (names.find()) {
			lastName = names.group();
		}
		return lastName;
	}

	public record DiscordMessageInfo(boolean discordMessage, String sender) {
	}
}
