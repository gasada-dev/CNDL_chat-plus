package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
final class ChatBindTest {
	@Test
	void captureStoresHeldModifierCombination() {
		ChatBind bind = new ChatBind();

		assertTrue(bind.capture(PlatformInput.nativeKey('K'),
				PlatformInput.controlModifier() | PlatformInput.shiftModifier()));
		assertTrue(bind.control);
		assertTrue(bind.shift);
		assertFalse(bind.alt);
		assertTrue(bind.keyCode == 'K');
	}

	@Test
	void captureKeepsWaitingForPrimaryKeyAfterModifier() {
		ChatBind bind = new ChatBind();

		assertFalse(bind.capture(PlatformInput.nativeLeftControl(), PlatformInput.controlModifier()));
	}

	@Test
	void moreSpecificCombinationIncludesItsSingleKeyBinding() {
		ChatBind combination = new ChatBind('K', true, true, false, "command");
		ChatBind singleKey = new ChatBind('K', "command");

		assertTrue(combination.includes(singleKey));
		assertFalse(singleKey.includes(combination));
	}
}
