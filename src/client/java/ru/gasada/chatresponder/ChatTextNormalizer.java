package ru.gasada.chatresponder;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ChatTextNormalizer {
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private ChatTextNormalizer() {
	}

	/**
	 * Preserves the matching normalization previously implemented by
	 * {@link ChatResponderEngine}: root-locale lowercase, trim and collapsed
	 * Java {@code \s} whitespace.
	 */
	public static String normalizeForMatching(String value) {
		Objects.requireNonNull(value, "value");
		return WHITESPACE.matcher(value.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
	}
}
