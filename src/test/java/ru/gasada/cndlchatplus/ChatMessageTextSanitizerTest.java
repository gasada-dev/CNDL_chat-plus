package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ChatMessageTextSanitizerTest {
	@Test
	void stripsChatHeadsAccessibilityLabel() {
		assertEquals("[17:17] (!) ZISSKAS » пропстите ночь пж",
				ChatMessageTextSanitizer.stripSyntheticLabels(
						"[17:17] (!) [ZISSKAS head]ZISSKAS » пропстите ночь пж"));
	}

	@Test
	void preservesOrdinaryBrackets() {
		assertEquals("[17:17] [VIP] Steve » message",
				ChatMessageTextSanitizer.stripSyntheticLabels("[17:17] [VIP] Steve » message"));
	}
}
