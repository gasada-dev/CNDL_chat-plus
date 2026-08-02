package ru.gasada.chatresponder;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class CompiledParserSettings {
	private final Optional<Pattern> discordMarker;
	private final Optional<Pattern> discordName;
	private final Optional<Pattern> lastSeen;
	private final Optional<Pattern> inactive;
	private final Optional<Pattern> lookupEnd;
	private final Optional<Pattern> lookupOutput;
	private final Optional<Pattern> timestampOnly;
	private final List<String> replyCandidateSeparators;

	private CompiledParserSettings(Optional<Pattern> discordMarker, Optional<Pattern> discordName,
			Optional<Pattern> lastSeen, Optional<Pattern> inactive, Optional<Pattern> lookupEnd,
			Optional<Pattern> lookupOutput, Optional<Pattern> timestampOnly,
			List<String> replyCandidateSeparators) {
		this.discordMarker = discordMarker;
		this.discordName = discordName;
		this.lastSeen = lastSeen;
		this.inactive = inactive;
		this.lookupEnd = lookupEnd;
		this.lookupOutput = lookupOutput;
		this.timestampOnly = timestampOnly;
		this.replyCandidateSeparators = replyCandidateSeparators;
	}

	public static CompiledParserSettings compile(ActiveTemplateSnapshot.ParserSnapshot source) {
		return new CompiledParserSettings(
				compileOne(source.discordMarkerPattern(), false),
				compileOne(source.discordNamePattern(), false),
				compileOne(source.lastSeenPattern(), true),
				compileOne(source.inactivePattern(), true),
				compileOne(source.lookupEndPattern(), false),
				compileOne(source.lookupOutputPattern(), false),
				compileOne(source.timestampOnlyPattern(), false),
				List.copyOf(source.replyCandidateSeparators()));
	}

	public static CompiledParserSettings compile(ParserSettings source) {
		return new CompiledParserSettings(
				compileOne(source.discordMarkerPattern, false),
				compileOne(source.discordNamePattern, false),
				compileOne(source.lastSeenPattern, true),
				compileOne(source.inactivePattern, true),
				compileOne(source.lookupEndPattern, false),
				compileOne(source.lookupOutputPattern, false),
				compileOne(source.timestampOnlyPattern, false),
				List.copyOf(source.replyCandidateSeparators));
	}

	private static Optional<Pattern> compileOne(String source, boolean captureRequired) {
		ParserPatternValidator.ValidationResult result = ParserPatternValidator.validate(source, captureRequired);
		return result.valid() ? Optional.of(result.pattern()) : Optional.empty();
	}

	public Optional<Pattern> discordMarker() {
		return discordMarker;
	}

	public Optional<Pattern> discordName() {
		return discordName;
	}

	public Optional<Pattern> lastSeen() {
		return lastSeen;
	}

	public Optional<Pattern> inactive() {
		return inactive;
	}

	public Optional<Pattern> lookupEnd() {
		return lookupEnd;
	}

	public Optional<Pattern> lookupOutput() {
		return lookupOutput;
	}

	public Optional<Pattern> timestampOnly() {
		return timestampOnly;
	}

	public List<String> replyCandidateSeparators() {
		return replyCandidateSeparators;
	}
}
