package ru.gasada.cndlchatplus;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class AmountValidator {
	public static final int MAX_INPUT_LENGTH = 16;
	private static final Pattern DECIMAL_AMOUNT = Pattern.compile("[0-9]+(?:[.,][0-9]{1,2})?");

	private AmountValidator() {
	}

	public static AmountValidationResult validate(String value) {
		if (value == null) {
			return AmountValidationResult.invalid("Сумма не задана");
		}

		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return AmountValidationResult.invalid("Сумма не должна быть пустой");
		}
		if (trimmed.length() > MAX_INPUT_LENGTH) {
			return AmountValidationResult.invalid("Сумма не должна быть длиннее 16 символов");
		}
		if (!DECIMAL_AMOUNT.matcher(trimmed).matches()) {
			return AmountValidationResult.invalid("Введите положительное число с точностью не более двух знаков");
		}

		String normalizedAmount = trimmed.replace(',', '.');
		BigDecimal amount = new BigDecimal(normalizedAmount);
		if (amount.signum() <= 0) {
			return AmountValidationResult.invalid("Сумма должна быть больше нуля");
		}
		return AmountValidationResult.success(normalizedAmount);
	}

	public record AmountValidationResult(boolean valid, String normalizedAmount, String errorMessage) {
		private static AmountValidationResult success(String normalizedAmount) {
			return new AmountValidationResult(true, normalizedAmount, "");
		}

		private static AmountValidationResult invalid(String errorMessage) {
			return new AmountValidationResult(false, null, errorMessage);
		}
	}
}
