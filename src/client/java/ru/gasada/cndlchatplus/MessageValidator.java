package ru.gasada.cndlchatplus;

import java.util.Objects;

public final class MessageValidator {
	public static final int MAX_PRIVATE_MESSAGE_LENGTH = 220;
	public static final int MAX_MAIL_LENGTH = 220;

	private MessageValidator() {
	}

	public static ValidationResult validate(String value, MessageType type) {
		Objects.requireNonNull(type, "type");
		if (value == null) {
			return ValidationResult.invalid("Сообщение не задано");
		}
		if (containsForbiddenControl(value)) {
			return ValidationResult.invalid("Сообщение содержит управляющие символы");
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return ValidationResult.invalid("Сообщение не должно быть пустым");
		}
		if (trimmed.length() > type.maxLength()) {
			return ValidationResult.invalid(
					"Сообщение не должно быть длиннее " + type.maxLength() + " символов");
		}
		return ValidationResult.success();
	}

	private static boolean containsForbiddenControl(String value) {
		return value.codePoints().anyMatch(MessageValidator::isForbiddenControl);
	}

	private static boolean isForbiddenControl(int codePoint) {
		int type = Character.getType(codePoint);
		return Character.isISOControl(codePoint)
				|| type == Character.FORMAT
				|| type == Character.LINE_SEPARATOR
				|| type == Character.PARAGRAPH_SEPARATOR;
	}

	public enum MessageType {
		PRIVATE_MESSAGE(MAX_PRIVATE_MESSAGE_LENGTH),
		MAIL(MAX_MAIL_LENGTH);

		private final int maxLength;

		MessageType(int maxLength) {
			this.maxLength = maxLength;
		}

		public int maxLength() {
			return maxLength;
		}
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
