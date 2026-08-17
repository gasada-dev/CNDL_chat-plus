package ru.gasada.cndlchatplus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private final Map<String, Pattern> playerInfoPatterns;
	private final Optional<Pattern> marriageEntry;
	private final Optional<Pattern> marriagePage;
	private final Optional<Pattern> marriageEmpty;

	private CompiledParserSettings(Optional<Pattern> discordMarker, Optional<Pattern> discordName,
			Optional<Pattern> lastSeen, Optional<Pattern> inactive, Optional<Pattern> lookupEnd,
			Optional<Pattern> lookupOutput, Optional<Pattern> timestampOnly,
			List<String> replyCandidateSeparators, Map<String, Pattern> playerInfoPatterns,
			Optional<Pattern> marriageEntry, Optional<Pattern> marriagePage,
			Optional<Pattern> marriageEmpty) {
		this.discordMarker = discordMarker;
		this.discordName = discordName;
		this.lastSeen = lastSeen;
		this.inactive = inactive;
		this.lookupEnd = lookupEnd;
		this.lookupOutput = lookupOutput;
		this.timestampOnly = timestampOnly;
		this.replyCandidateSeparators = replyCandidateSeparators;
		this.playerInfoPatterns = playerInfoPatterns;
		this.marriageEntry = marriageEntry;
		this.marriagePage = marriagePage;
		this.marriageEmpty = marriageEmpty;
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
				List.copyOf(source.replyCandidateSeparators()), compileAll(source.playerInfoPatterns()),
				compileOne(source.marriageEntryPattern(), 2),
				compileOne(source.marriagePagePattern(), 2),
				compileOne(source.marriageEmptyPattern(), 0));
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
				List.copyOf(source.replyCandidateSeparators), compileAll(source.playerInfoPatterns),
				compileOne(source.marriageEntryPattern, 2), compileOne(source.marriagePagePattern, 2),
				compileOne(source.marriageEmptyPattern, 0));
	}

	private static Optional<Pattern> compileOne(String source, boolean captureRequired) {
		return compileOne(source, captureRequired ? 1 : 0);
	}

	private static Optional<Pattern> compileOne(String source, int minimumCaptureGroups) {
		ParserPatternValidator.ValidationResult result = ParserPatternValidator.validate(source, minimumCaptureGroups);
		return result.valid() ? Optional.of(result.pattern()) : Optional.empty();
	}

	private static Map<String, Pattern> compileAll(Map<String, String> source) {
		Map<String, Pattern> compiled = new LinkedHashMap<>();
		if (source == null) return Map.of();
		for (Map.Entry<String, String> entry : source.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) continue;
			ParserPatternValidator.ValidationResult result = ParserPatternValidator.validate(entry.getValue(), true);
			if (result.valid()) compiled.put(entry.getKey(), result.pattern());
		}
		return Map.copyOf(compiled);
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

	public Map<String, Pattern> playerInfoPatterns() {
		return playerInfoPatterns;
	}

	public Optional<Pattern> marriageEntry() { return marriageEntry; }
	public Optional<Pattern> marriagePage() { return marriagePage; }
	public Optional<Pattern> marriageEmpty() { return marriageEmpty; }
}
