package ru.gasada.cndlchatplus;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class CompiledWildcard {
	private final String source;
	private final WildcardMatchMode mode;
	private final Pattern pattern;

	CompiledWildcard(String source, WildcardMatchMode mode, Pattern pattern) {
		this.source = Objects.requireNonNull(source, "source");
		this.mode = Objects.requireNonNull(mode, "mode");
		this.pattern = Objects.requireNonNull(pattern, "pattern");
	}

	public String source() {
		return source;
	}

	public boolean matches(String text) {
		Objects.requireNonNull(text, "text");
		String normalizedText = switch (mode) {
			case FULL_MATCH -> ChatTextNormalizer.normalizeForMatching(text);
			case CONTAINS_MATCH -> text.toLowerCase(Locale.ROOT);
		};
		return pattern.matcher(normalizedText).find();
	}
}
