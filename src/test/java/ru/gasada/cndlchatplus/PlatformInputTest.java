package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

final class PlatformInputTest {
	private static final int GLFW_KEY_KP_DECIMAL = 330;
	private static final int SDL_SCANCODE_KP_PERIOD = 99;

	@Test
	void mapsLegacyStoredKeysToTargetNativeCodes() {
		assertEquals(InputConstants.KEY_F7, PlatformInput.nativeKey(PlatformInput.KEY_F7));
		assertEquals(InputConstants.KEY_BACKSLASH, PlatformInput.nativeKey(PlatformInput.KEY_BACKSLASH));
		assertEquals(PlatformInput.KEY_F7, PlatformInput.storageKey(InputConstants.KEY_F7));
		assertEquals(PlatformInput.KEY_BACKSLASH, PlatformInput.storageKey(InputConstants.KEY_BACKSLASH));
	}

	@Test
	void mapsLegacyKeypadDecimalToPhysicalPeriodKey() {
		int expectedNativeKey = "KEYBOARD".equals(PlatformInput.keyType().name())
				? SDL_SCANCODE_KP_PERIOD : GLFW_KEY_KP_DECIMAL;
		assertEquals(expectedNativeKey, PlatformInput.nativeKey(GLFW_KEY_KP_DECIMAL));
		assertEquals(GLFW_KEY_KP_DECIMAL, PlatformInput.storageKey(expectedNativeKey));
	}

	@Test
	void readsModifierMasks() {
		assertTrue(PlatformInput.control(PlatformInput.controlModifier()));
		assertTrue(PlatformInput.shift(PlatformInput.shiftModifier()));
	}

	@Test
	void preservesLegacyInternationalKeys() {
		boolean sdl = "KEYBOARD".equals(PlatformInput.keyType().name());
		int world1 = sdl ? 50 : 161;
		int world2 = sdl ? 100 : 162;
		assertEquals(world1, PlatformInput.nativeKey(161));
		assertEquals(world2, PlatformInput.nativeKey(162));
		assertEquals(161, PlatformInput.storageKey(world1));
		assertEquals(162, PlatformInput.storageKey(world2));
	}
}
