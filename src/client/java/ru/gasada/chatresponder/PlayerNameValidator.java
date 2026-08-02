package ru.gasada.chatresponder;

import java.util.regex.Pattern;

public final class PlayerNameValidator {
	public static final int MAX_LENGTH = 16;
	private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

	private PlayerNameValidator() {
	}

	public static ValidationResult validate(String value) {
		if (value == null) {
			return ValidationResult.invalid("Ник не задан");
		}
		if (value.isEmpty()) {
			return ValidationResult.invalid("Ник не должен быть пустым");
		}
		if (value.length() > MAX_LENGTH) {
			return ValidationResult.invalid("Ник не должен быть длиннее 16 символов");
		}
		if (!PLAYER_NAME.matcher(value).matches()) {
			return ValidationResult.invalid("Ник может содержать только латинские буквы, цифры и _");
		}
		return ValidationResult.success();
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
