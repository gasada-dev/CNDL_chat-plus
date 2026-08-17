package ru.gasada.cndlchatplus;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ParserPatternValidator {
	public static final int MAX_PATTERN_LENGTH = 4096;

	private ParserPatternValidator() {
	}

	public static ValidationResult validate(String source, boolean captureRequired) {
		return validate(source, captureRequired ? 1 : 0);
	}

	public static ValidationResult validate(String source, int minimumCaptureGroups) {
		if (source == null || source.isBlank()) {
			return ValidationResult.failure("Parser pattern не задан");
		}
		if (source.length() > MAX_PATTERN_LENGTH) {
			return ValidationResult.failure("Parser pattern превышает 4096 символов");
		}
		if (source.codePoints().anyMatch(codePoint -> codePoint == 0)) {
			return ValidationResult.failure("Parser pattern содержит NUL");
		}
		try {
			Pattern compiled = Pattern.compile(source);
			if (compiled.matcher("").groupCount() < minimumCaptureGroups) {
				return ValidationResult.failure("Parser pattern должен содержать capture groups: "
						+ minimumCaptureGroups);
			}
			return ValidationResult.success(compiled);
		} catch (PatternSyntaxException error) {
			return ValidationResult.failure("Некорректный parser pattern: " + error.getDescription());
		}
	}

	public record ValidationResult(boolean valid, Pattern pattern, String errorMessage) {
		private static ValidationResult success(Pattern pattern) {
			return new ValidationResult(true, pattern, "");
		}

		private static ValidationResult failure(String errorMessage) {
			return new ValidationResult(false, null, errorMessage);
		}
	}
}
