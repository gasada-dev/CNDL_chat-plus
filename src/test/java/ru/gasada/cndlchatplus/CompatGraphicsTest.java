package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CompatGraphicsTest {
	@Test
	void roundedInsetKeepsSquareAndClampsRadius() {
		assertEquals(0, CompatGraphics.roundedInset(10, 6, 0, 0));
		assertEquals(1, CompatGraphics.roundedInset(10, 6, 3, 0));
		assertEquals(0, CompatGraphics.roundedInset(10, 6, 3, 2));
		assertEquals(1, CompatGraphics.roundedInset(10, 6, 99, 5));
		assertEquals(0, CompatGraphics.roundedInset(-1, 6, 3, 0));
	}
}
