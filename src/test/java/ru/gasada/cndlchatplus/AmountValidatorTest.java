package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AmountValidatorTest {
	@Test
	void acceptsPositiveIntegerAndDecimalValues() {
		assertNormalized("10", "10");
		assertNormalized("10.25", "10.25");
		assertNormalized("0.01", "0.01");
	}

	@Test
	void convertsCommaToDotWithoutDoubleFormatting() {
		assertNormalized("  0010,50  ", "0010.50");
	}

	@Test
	void rejectsZeroInAllSupportedRepresentations() {
		assertInvalid("0");
		assertInvalid("0.00");
		assertInvalid("00,00");
	}

	@Test
	void rejectsNegativeAndExplicitlySignedValues() {
		assertInvalid("-1");
		assertInvalid("+1");
	}

	@Test
	void rejectsMoreThanTwoFractionDigitsAndIncompleteDecimals() {
		assertInvalid("1.234");
		assertInvalid("1.");
		assertInvalid(".5");
	}

	@Test
	void rejectsExponentNaNAndInfinity() {
		assertInvalid("1e3");
		assertInvalid("NaN");
		assertInvalid("Infinity");
	}

	@Test
	void rejectsSpacesInsideNumber() {
		assertInvalid("1 000");
		assertInvalid("10 .25");
	}

	@Test
	void enforcesCurrentSixteenCharacterInputLimit() {
		assertNormalized("1234567890123456", "1234567890123456");
		assertInvalid("12345678901234567");
	}

	@Test
	void rejectsNullEmptyUnicodeAndControlCharacters() {
		assertInvalid(null);
		assertInvalid("");
		assertInvalid("   ");
		assertInvalid("１２");
		assertInvalid("1\n2");
	}

	private static void assertNormalized(String input, String expected) {
		AmountValidator.AmountValidationResult result = AmountValidator.validate(input);
		assertTrue(result.valid());
		assertEquals(expected, result.normalizedAmount());
		assertTrue(result.errorMessage().isEmpty());
	}

	private static void assertInvalid(String input) {
		AmountValidator.AmountValidationResult result = AmountValidator.validate(input);
		assertFalse(result.valid());
		assertNull(result.normalizedAmount());
		assertFalse(result.errorMessage().isBlank());
	}
}
