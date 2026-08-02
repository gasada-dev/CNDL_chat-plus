package ru.gasada.chatresponder;

import java.util.regex.Matcher;

public final class FriendLookupParser {
	private final CompiledParserSettings settings;

	public FriendLookupParser(CompiledParserSettings settings) {
		this.settings = settings;
	}

	public ParseResult parse(String text) {
		if (text == null) {
			return new ParseResult(MessageType.UNRELATED, null);
		}
		if (text.isBlank() || settings.timestampOnly().map(pattern -> pattern.matcher(text).matches()).orElse(false)) {
			return new ParseResult(MessageType.EMPTY_OR_TIMESTAMP, null);
		}
		ParseResult lastSeen = captured(settings.lastSeen(), text, MessageType.LAST_SEEN);
		if (lastSeen != null) {
			return lastSeen;
		}
		ParseResult inactive = captured(settings.inactive(), text, MessageType.INACTIVE);
		if (inactive != null) {
			return inactive;
		}
		if (settings.lookupEnd().map(pattern -> pattern.matcher(text).find()).orElse(false)) {
			return new ParseResult(MessageType.LOOKUP_END, null);
		}
		if (settings.lookupOutput().map(pattern -> pattern.matcher(text).find()).orElse(false)) {
			return new ParseResult(MessageType.LOOKUP_OUTPUT, null);
		}
		return new ParseResult(MessageType.UNRELATED, null);
	}

	private static ParseResult captured(java.util.Optional<java.util.regex.Pattern> pattern, String text,
			MessageType type) {
		if (pattern.isEmpty()) {
			return null;
		}
		Matcher matcher = pattern.get().matcher(text);
		return matcher.find() ? new ParseResult(type, matcher.group(1).trim()) : null;
	}

	public enum MessageType {
		EMPTY_OR_TIMESTAMP,
		LAST_SEEN,
		INACTIVE,
		LOOKUP_END,
		LOOKUP_OUTPUT,
		UNRELATED
	}

	public record ParseResult(MessageType type, String value) {
	}
}
