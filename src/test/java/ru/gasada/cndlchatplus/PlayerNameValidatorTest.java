package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PlayerNameValidatorTest {
	@Test
	void acceptsAsciiLettersDigitsAndUnderscore() {
		assertTrue(PlayerNameValidator.validate("Player_123").valid());
	}

	@Test
	void acceptsExactlySixteenCharacters() {
		assertTrue(PlayerNameValidator.validate("A123456789012345").valid());
	}

	@Test
	void rejectsSeventeenCharacters() {
		assertInvalid("A1234567890123456");
	}

	@Test
	void rejectsNullAndEmptyValues() {
		assertInvalid(null);
		assertInvalid("");
	}

	@Test
	void rejectsSpacesAndDoesNotTrimInput() {
		assertInvalid(" Player");
		assertInvalid("Player Name");
	}

	@Test
	void rejectsLineBreaksAndUnicodeCharacters() {
		assertInvalid("Player\nName");
		assertInvalid("Игрок");
	}

	private static void assertInvalid(String value) {
		PlayerNameValidator.ValidationResult result = PlayerNameValidator.validate(value);
		assertFalse(result.valid());
		assertFalse(result.errorMessage().isBlank());
	}
}
