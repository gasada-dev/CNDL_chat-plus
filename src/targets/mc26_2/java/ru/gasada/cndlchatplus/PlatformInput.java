package ru.gasada.cndlchatplus;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class PlatformInput {
	static final int KEY_F6 = GLFW.GLFW_KEY_F6;
	static final int KEY_F7 = GLFW.GLFW_KEY_F7;
	static final int KEY_BACKSLASH = GLFW.GLFW_KEY_BACKSLASH;

	private PlatformInput() { }

	static int storageKey(int key) { return key; }
	static int nativeKey(int storedKey) { return storedKey; }
	static int nativeLeftControl() { return GLFW.GLFW_KEY_LEFT_CONTROL; }
	static int controlModifier() { return GLFW.GLFW_MOD_CONTROL; }
	static int shiftModifier() { return GLFW.GLFW_MOD_SHIFT; }
	static boolean modifierKey(int key) {
		return key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
				|| key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
				|| key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT;
	}
	static boolean control(int modifiers) { return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0; }
	static boolean shift(int modifiers) { return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0; }
	static boolean alt(int modifiers) { return (modifiers & GLFW.GLFW_MOD_ALT) != 0; }
	static InputConstants.Type keyType() { return InputConstants.Type.KEYSYM; }
	static int nativeF8() { return InputConstants.KEY_F8; }
	public static int nativeF() { return GLFW.GLFW_KEY_F; }
	public static int nativeEscape() { return GLFW.GLFW_KEY_ESCAPE; }
	static String keyName(int storedKey) {
		return InputConstants.Type.KEYSYM.getOrCreate(storedKey).getDisplayName().getString();
	}
	static boolean keyDown(Minecraft minecraft, int storedKey) {
		return InputConstants.isKeyDown(minecraft.getWindow(), storedKey);
	}
	static boolean controlDown(Minecraft minecraft) {
		return down(minecraft, InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
	}
	static boolean shiftDown(Minecraft minecraft) {
		return down(minecraft, InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
	}
	static boolean altDown(Minecraft minecraft) {
		return down(minecraft, InputConstants.KEY_LALT, InputConstants.KEY_RALT);
	}
	public static boolean leftMouseDown(Minecraft minecraft) {
		return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
				== GLFW.GLFW_PRESS;
	}

	private static boolean down(Minecraft minecraft, int left, int right) {
		return InputConstants.isKeyDown(minecraft.getWindow(), left)
				|| InputConstants.isKeyDown(minecraft.getWindow(), right);
	}
}
