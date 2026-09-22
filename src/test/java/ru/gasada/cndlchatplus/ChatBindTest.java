package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

final class ChatBindTest {
	@Test
	void captureStoresHeldModifierCombination() {
		ChatBind bind = new ChatBind();

		assertTrue(bind.capture(GLFW.GLFW_KEY_K, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT));
		assertTrue(bind.control);
		assertTrue(bind.shift);
		assertFalse(bind.alt);
	}

	@Test
	void captureKeepsWaitingForPrimaryKeyAfterModifier() {
		ChatBind bind = new ChatBind();

		assertFalse(bind.capture(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_MOD_CONTROL));
	}

	@Test
	void moreSpecificCombinationIncludesItsSingleKeyBinding() {
		ChatBind combination = new ChatBind(GLFW.GLFW_KEY_K, true, true, false, "command");
		ChatBind singleKey = new ChatBind(GLFW.GLFW_KEY_K, "command");

		assertTrue(combination.includes(singleKey));
		assertFalse(singleKey.includes(combination));
	}
}
