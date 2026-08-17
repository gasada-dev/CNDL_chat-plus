package ru.gasada.cndlchatplus;

import java.util.regex.Pattern;

public final class DiscordNameValidator {
	public static final int MAX_LENGTH = 32;
	private static final Pattern DISCORD_NAME = Pattern.compile("[\\p{L}\\p{N}_]+");

	private DiscordNameValidator() {
	}

	public static ValidationResult validate(String value) {
		if (value == null) {
			return ValidationResult.invalid("Discord-имя не задано");
		}
		if (containsForbiddenControl(value)) {
			return ValidationResult.invalid("Discord-имя содержит управляющие символы");
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return ValidationResult.invalid("Discord-имя не должно быть пустым");
		}
		if (trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) {
			return ValidationResult.invalid("Discord-имя не должно быть длиннее 32 символов");
		}
		if (!DISCORD_NAME.matcher(trimmed).matches()) {
			return ValidationResult.invalid(
					"Discord-имя может содержать только Unicode-буквы, цифры и _ без пробелов");
		}
		return ValidationResult.success();
	}

	private static boolean containsForbiddenControl(String value) {
		return value.codePoints().anyMatch(DiscordNameValidator::isForbiddenControl);
	}

	private static boolean isForbiddenControl(int codePoint) {
		int type = Character.getType(codePoint);
		return Character.isISOControl(codePoint)
				|| type == Character.FORMAT
				|| type == Character.LINE_SEPARATOR
				|| type == Character.PARAGRAPH_SEPARATOR;
	}

	public record ValidationResult(boolean valid, String errorMessage) {
		private static ValidationResult success() {
			return new ValidationResult(true, "");
		}

		private static ValidationResult invalid(String errorMessage) {
			return new ValidationResult(false, errorMessage);
		}
	}
}
