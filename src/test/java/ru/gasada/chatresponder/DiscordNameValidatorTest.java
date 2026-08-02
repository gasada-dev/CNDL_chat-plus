package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DiscordNameValidatorTest {
	@Test
	void acceptsCurrentUiCharacterSetIncludingUnicode() {
		assertTrue(DiscordNameValidator.validate("Игрок_玩家42").valid());
	}

	@Test
	void trimsBeforeValidationWithoutChangingCase() {
		String value = "  ÄName  ";
		assertTrue(DiscordNameValidator.validate(value).valid());
		assertTrue(value.contains("ÄN"));
	}

	@Test
	void acceptsThirtyTwoCodePointsAndRejectsThirtyThree() {
		assertTrue(DiscordNameValidator.validate("я".repeat(32)).valid());
		assertInvalid("я".repeat(33));
	}

	@Test
	void rejectsNullAndBlankValues() {
		assertInvalid(null);
		assertInvalid("");
		assertInvalid("   ");
	}

	@Test
	void rejectsSpacesBecauseCurrentUiDoesNotAllowThem() {
		assertInvalid("Discord Name");
	}

	@Test
	void rejectsCrLfNulAndOtherControls() {
		assertInvalid("Name\r");
		assertInvalid("Name\n");
		assertInvalid("Name\0");
		assertInvalid("Name\u0001");
	}

	@Test
	void rejectsPunctuationOutsideCurrentUiCharacterSet() {
		assertInvalid("name-tag");
		assertInvalid("name#1");
	}

	private static void assertInvalid(String value) {
		DiscordNameValidator.ValidationResult result = DiscordNameValidator.validate(value);
		assertFalse(result.valid());
		assertFalse(result.errorMessage().isBlank());
	}
}
