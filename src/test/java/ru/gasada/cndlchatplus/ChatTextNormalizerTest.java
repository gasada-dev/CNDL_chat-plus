package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ChatTextNormalizerTest {
	@Test
	void usesRootLocaleLowercaseForUnicodeText() {
		assertEquals("ёжик i", ChatTextNormalizer.normalizeForMatching("ЁЖИК I"));
	}

	@Test
	void trimsAndCollapsesExistingWhitespaceSemantics() {
		assertEquals("привет мир", ChatTextNormalizer.normalizeForMatching(" \tПривет\n\r  мир \f"));
	}

	@Test
	void preservesPunctuationAndRegexMetacharacters() {
		String value = "  Цена: .+?[x] — (тест)!  ";
		assertEquals("цена: .+?[x] — (тест)!", ChatTextNormalizer.normalizeForMatching(value));
	}

	@Test
	void doesNotTransliterateOrCorrectKeyboardLayout() {
		assertEquals("руддщ привет", ChatTextNormalizer.normalizeForMatching("РУДДЩ привет"));
	}

	@Test
	void rejectsNullThroughExplicitContract() {
		assertThrows(NullPointerException.class, () -> ChatTextNormalizer.normalizeForMatching(null));
	}
}
