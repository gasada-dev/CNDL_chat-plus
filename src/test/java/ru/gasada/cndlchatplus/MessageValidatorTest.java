package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MessageValidatorTest {
	@Test
	void acceptsUnicodeAndPunctuation() {
		assertValid("Привет, 世界!", MessageValidator.MessageType.PRIVATE_MESSAGE);
	}

	@Test
	void rejectsNullEmptyAndTrimmedEmptyValues() {
		assertInvalid(null, MessageValidator.MessageType.PRIVATE_MESSAGE);
		assertInvalid("", MessageValidator.MessageType.PRIVATE_MESSAGE);
		assertInvalid("   ", MessageValidator.MessageType.MAIL);
	}

	@Test
	void rejectsCrLfNulAndDangerousControls() {
		assertInvalid("строка\rвторая", MessageValidator.MessageType.PRIVATE_MESSAGE);
		assertInvalid("строка\nвторая", MessageValidator.MessageType.PRIVATE_MESSAGE);
		assertInvalid("текст\0хвост", MessageValidator.MessageType.MAIL);
		assertInvalid("текст\u0001", MessageValidator.MessageType.PRIVATE_MESSAGE);
		assertInvalid("текст\u202E", MessageValidator.MessageType.MAIL);
	}

	@Test
	void privateMessageUsesCurrentTwoHundredTwentyCharacterLimit() {
		assertValid("a".repeat(220), MessageValidator.MessageType.PRIVATE_MESSAGE);
		assertInvalid("a".repeat(221), MessageValidator.MessageType.PRIVATE_MESSAGE);
	}

	@Test
	void mailUsesCurrentTwoHundredTwentyCharacterLimit() {
		assertValid("a".repeat(220), MessageValidator.MessageType.MAIL);
		assertInvalid("a".repeat(221), MessageValidator.MessageType.MAIL);
	}

	private static void assertValid(String value, MessageValidator.MessageType type) {
		assertTrue(MessageValidator.validate(value, type).valid());
	}

	private static void assertInvalid(String value, MessageValidator.MessageType type) {
		MessageValidator.ValidationResult result = MessageValidator.validate(value, type);
		assertFalse(result.valid());
		assertFalse(result.errorMessage().isBlank());
	}
}
