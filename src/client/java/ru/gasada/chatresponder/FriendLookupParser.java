package ru.gasada.chatresponder;

import java.util.regex.Matcher;
import java.util.Map;

public final class FriendLookupParser {
	private final CompiledParserSettings settings;

	public FriendLookupParser(CompiledParserSettings settings) {
		this.settings = settings;
	}

	public ParseResult parse(String text) {
		if (text == null) {
			return new ParseResult(MessageType.UNRELATED, null, null);
		}
		if (text.isBlank() || settings.timestampOnly().map(pattern -> pattern.matcher(text).matches()).orElse(false)) {
			return new ParseResult(MessageType.EMPTY_OR_TIMESTAMP, null, null);
		}
		ParseResult lastSeen = captured(settings.lastSeen(), text, MessageType.LAST_SEEN);
		if (lastSeen != null) {
			return lastSeen;
		}
		ParseResult inactive = captured(settings.inactive(), text, MessageType.INACTIVE);
		if (inactive != null) {
			return inactive;
		}
		for (Map.Entry<String, java.util.regex.Pattern> entry : settings.playerInfoPatterns().entrySet()) {
			Matcher matcher = entry.getValue().matcher(text);
			if (matcher.find()) {
				return new ParseResult(MessageType.PLAYER_INFO_FIELD, matcher.group(1).trim(), entry.getKey());
			}
		}
		if (settings.lookupEnd().map(pattern -> pattern.matcher(text).find()).orElse(false)) {
			return new ParseResult(MessageType.LOOKUP_END, null, null);
		}
		if (settings.lookupOutput().map(pattern -> pattern.matcher(text).find()).orElse(false)) {
			return new ParseResult(MessageType.LOOKUP_OUTPUT, null, null);
		}
		return new ParseResult(MessageType.UNRELATED, null, null);
	}

	public boolean isLookupEnd(String text) {
		return text != null && settings.lookupEnd().map(pattern -> pattern.matcher(text).find()).orElse(false);
	}

	private static ParseResult captured(java.util.Optional<java.util.regex.Pattern> pattern, String text,
			MessageType type) {
		if (pattern.isEmpty()) {
			return null;
		}
		Matcher matcher = pattern.get().matcher(text);
		return matcher.find() ? new ParseResult(type, matcher.group(1).trim(), null) : null;
	}

	public enum MessageType {
		EMPTY_OR_TIMESTAMP,
		LAST_SEEN,
		INACTIVE,
		LOOKUP_END,
		LOOKUP_OUTPUT,
		PLAYER_INFO_FIELD,
		UNRELATED
	}

	public record ParseResult(MessageType type, String value, String fieldName) {
	}
}
