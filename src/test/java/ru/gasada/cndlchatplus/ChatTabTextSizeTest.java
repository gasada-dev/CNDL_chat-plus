package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ChatTabTextSizeTest {
	@Test
	void parsesCustomPercentageWithoutSeparatingLegacyPresets() {
		assertEquals(135, ChatTabTextSize.parsePercent("135%"));
		assertEquals(80, ChatTabTextSize.SMALL.percent());
		assertEquals(null, ChatTabTextSize.parsePercent("one hundred"));
	}
}
