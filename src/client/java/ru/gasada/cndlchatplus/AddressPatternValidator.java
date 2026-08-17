package ru.gasada.cndlchatplus;

public final class AddressPatternValidator {
	public static final int MAX_LENGTH = 253 + 6;

	private AddressPatternValidator() {
	}

	public static ValidationResult validate(String value) {
		if (value == null) {
			return ValidationResult.failure("Address pattern не задан");
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return ValidationResult.failure("Address pattern не должен быть пустым");
		}
		if (trimmed.length() > MAX_LENGTH) {
			return ValidationResult.failure("Address pattern слишком длинный");
		}

		boolean wildcard = trimmed.startsWith("*.");
		if (trimmed.contains("*") && !wildcard) {
			return ValidationResult.failure("Wildcard разрешён только как левый префикс *.");
		}
		String address = wildcard ? trimmed.substring(2) : trimmed;
		if (address.contains("*")) {
			return ValidationResult.failure("Допускается только один wildcard label");
		}
		ServerAddressNormalizer.NormalizationResult normalized = ServerAddressNormalizer.normalize(address);
		if (!normalized.valid()) {
			return ValidationResult.failure(normalized.errorMessage());
		}
		String normalizedPattern = wildcard ? "*." + normalized.normalizedAddress() : normalized.normalizedAddress();
		return ValidationResult.success(normalizedPattern, wildcard);
	}

	public record ValidationResult(boolean valid, String normalizedPattern, boolean wildcard,
			String errorMessage) {
		private static ValidationResult success(String normalizedPattern, boolean wildcard) {
			return new ValidationResult(true, normalizedPattern, wildcard, "");
		}

		private static ValidationResult failure(String errorMessage) {
			return new ValidationResult(false, null, false, errorMessage);
		}
	}
}
