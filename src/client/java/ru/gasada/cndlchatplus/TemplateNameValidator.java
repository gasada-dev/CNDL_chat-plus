package ru.gasada.cndlchatplus;

public final class TemplateNameValidator {
	public static final int MAX_LENGTH = 64;

	private TemplateNameValidator() {
	}

	public static ValidationResult validate(String value) {
		if (value == null) {
			return ValidationResult.failure("Имя шаблона не задано");
		}
		if (value.codePoints().anyMatch(Character::isISOControl)) {
			return ValidationResult.failure("Имя шаблона содержит управляющие символы");
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return ValidationResult.failure("Имя шаблона не должно быть пустым");
		}
		if (trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) {
			return ValidationResult.failure("Имя шаблона не должно быть длиннее 64 символов");
		}
		return ValidationResult.success(trimmed);
	}

	public record ValidationResult(boolean valid, String normalizedName, String errorMessage) {
		private static ValidationResult success(String normalizedName) {
			return new ValidationResult(true, normalizedName, "");
		}

		private static ValidationResult failure(String errorMessage) {
			return new ValidationResult(false, null, errorMessage);
		}
	}
}
