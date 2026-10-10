package ru.gasada.cndlchatplus;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLScancode;

public final class PlatformInput {
	static final int KEY_F6 = 295;
	static final int KEY_F7 = 296;
	static final int KEY_BACKSLASH = 92;

	private PlatformInput() { }

	static int storageKey(int key) {
		if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) return 65 + key - InputConstants.KEY_A;
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) return 49 + key - InputConstants.KEY_1;
		if (key == InputConstants.KEY_0) return 48;
		if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) return 290 + key - InputConstants.KEY_F1;
		if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) return 302 + key - InputConstants.KEY_F13;
		if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
			return 321 + key - InputConstants.KEY_NUMPAD1;
		}
		if (key == InputConstants.KEY_NUMPAD0) return 320;
		return switch (key) {
			case InputConstants.KEY_SPACE -> 32;
			case InputConstants.KEY_APOSTROPHE -> 39;
			case InputConstants.KEY_COMMA -> 44;
			case InputConstants.KEY_MINUS -> 45;
			case InputConstants.KEY_PERIOD -> 46;
			case InputConstants.KEY_SLASH -> 47;
			case InputConstants.KEY_SEMICOLON -> 59;
			case InputConstants.KEY_EQUALS -> 61;
			case InputConstants.KEY_LBRACKET -> 91;
			case InputConstants.KEY_BACKSLASH -> 92;
			case InputConstants.KEY_RBRACKET -> 93;
			case InputConstants.KEY_GRAVE -> 96;
			case SDLScancode.SDL_SCANCODE_NONUSHASH -> 161;
			case SDLScancode.SDL_SCANCODE_NONUSBACKSLASH -> 162;
			case InputConstants.KEY_ESCAPE -> 256;
			case InputConstants.KEY_RETURN -> 257;
			case InputConstants.KEY_TAB -> 258;
			case InputConstants.KEY_BACKSPACE -> 259;
			case InputConstants.KEY_INSERT -> 260;
			case InputConstants.KEY_DELETE -> 261;
			case InputConstants.KEY_RIGHT -> 262;
			case InputConstants.KEY_LEFT -> 263;
			case InputConstants.KEY_DOWN -> 264;
			case InputConstants.KEY_UP -> 265;
			case InputConstants.KEY_PAGEUP -> 266;
			case InputConstants.KEY_PAGEDOWN -> 267;
			case InputConstants.KEY_HOME -> 268;
			case InputConstants.KEY_END -> 269;
			case InputConstants.KEY_CAPSLOCK -> 280;
			case InputConstants.KEY_SCROLLLOCK -> 281;
			case InputConstants.KEY_NUMLOCK -> 282;
			case InputConstants.KEY_PRINTSCREEN -> 283;
			case InputConstants.KEY_PAUSE -> 284;
			case SDLScancode.SDL_SCANCODE_KP_PERIOD -> 330;
			case SDLScancode.SDL_SCANCODE_KP_DIVIDE -> 331;
			case InputConstants.KEY_MULTIPLY -> 332;
			case SDLScancode.SDL_SCANCODE_KP_MINUS -> 333;
			case InputConstants.KEY_ADD -> 334;
			case InputConstants.KEY_NUMPADENTER -> 335;
			case InputConstants.KEY_NUMPADEQUALS -> 336;
			case InputConstants.KEY_LSHIFT -> 340;
			case InputConstants.KEY_LCONTROL -> 341;
			case InputConstants.KEY_LALT -> 342;
			case InputConstants.KEY_LGUI -> 343;
			case InputConstants.KEY_RSHIFT -> 344;
			case InputConstants.KEY_RCONTROL -> 345;
			case InputConstants.KEY_RALT -> 346;
			case InputConstants.KEY_RGUI -> 347;
			case SDLScancode.SDL_SCANCODE_APPLICATION -> 348;
			default -> 0;
		};
	}

	static boolean modifierKey(int key) {
		return key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
				|| key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
				|| key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT;
	}
	static int nativeLeftControl() { return InputConstants.KEY_LCONTROL; }
	static int controlModifier() { return InputConstants.MOD_CONTROL; }
	static int shiftModifier() { return InputConstants.MOD_SHIFT; }
	static boolean control(int modifiers) { return (modifiers & InputConstants.MOD_CONTROL) != 0; }
	static boolean shift(int modifiers) { return (modifiers & InputConstants.MOD_SHIFT) != 0; }
	static boolean alt(int modifiers) { return (modifiers & InputConstants.MOD_ALT) != 0; }
	static InputConstants.Type keyType() { return InputConstants.Type.KEYBOARD; }
	static int nativeF8() { return InputConstants.KEY_F8; }
	public static int nativeF() { return InputConstants.KEY_F; }
	public static int nativeEscape() { return InputConstants.KEY_ESCAPE; }
	static String keyName(int storedKey) {
		int nativeKey = nativeKey(storedKey);
		return nativeKey == 0 ? "Неизвестная клавиша"
				: InputConstants.Type.KEYBOARD.getOrCreate(nativeKey).getDisplayName().getString();
	}
	static boolean keyDown(Minecraft minecraft, int storedKey) {
		int nativeKey = nativeKey(storedKey);
		return nativeKey != 0 && InputConstants.isKeyDown(nativeKey);
	}
	static boolean controlDown(Minecraft minecraft) {
		return down(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
	}
	static boolean shiftDown(Minecraft minecraft) {
		return down(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
	}
	static boolean altDown(Minecraft minecraft) {
		return down(InputConstants.KEY_LALT, InputConstants.KEY_RALT);
	}
	public static boolean leftMouseDown(Minecraft minecraft) { return minecraft.mouseHandler.isLeftPressed(); }

	private static boolean down(int left, int right) {
		return InputConstants.isKeyDown(left) || InputConstants.isKeyDown(right);
	}

	static int nativeKey(int storedKey) {
		if (storedKey >= 65 && storedKey <= 90) return InputConstants.KEY_A + storedKey - 65;
		if (storedKey >= 49 && storedKey <= 57) return InputConstants.KEY_1 + storedKey - 49;
		if (storedKey == 48) return InputConstants.KEY_0;
		if (storedKey >= 290 && storedKey <= 301) return InputConstants.KEY_F1 + storedKey - 290;
		if (storedKey >= 302 && storedKey <= 313) return InputConstants.KEY_F13 + storedKey - 302;
		if (storedKey >= 321 && storedKey <= 329) return InputConstants.KEY_NUMPAD1 + storedKey - 321;
		if (storedKey == 320) return InputConstants.KEY_NUMPAD0;
		return switch (storedKey) {
			case 32 -> InputConstants.KEY_SPACE;
			case 39 -> InputConstants.KEY_APOSTROPHE;
			case 44 -> InputConstants.KEY_COMMA;
			case 45 -> InputConstants.KEY_MINUS;
			case 46 -> InputConstants.KEY_PERIOD;
			case 47 -> InputConstants.KEY_SLASH;
			case 59 -> InputConstants.KEY_SEMICOLON;
			case 61 -> InputConstants.KEY_EQUALS;
			case 91 -> InputConstants.KEY_LBRACKET;
			case 92 -> InputConstants.KEY_BACKSLASH;
			case 93 -> InputConstants.KEY_RBRACKET;
			case 96 -> InputConstants.KEY_GRAVE;
			case 161 -> SDLScancode.SDL_SCANCODE_NONUSHASH;
			case 162 -> SDLScancode.SDL_SCANCODE_NONUSBACKSLASH;
			case 256 -> InputConstants.KEY_ESCAPE;
			case 257 -> InputConstants.KEY_RETURN;
			case 258 -> InputConstants.KEY_TAB;
			case 259 -> InputConstants.KEY_BACKSPACE;
			case 260 -> InputConstants.KEY_INSERT;
			case 261 -> InputConstants.KEY_DELETE;
			case 262 -> InputConstants.KEY_RIGHT;
			case 263 -> InputConstants.KEY_LEFT;
			case 264 -> InputConstants.KEY_DOWN;
			case 265 -> InputConstants.KEY_UP;
			case 266 -> InputConstants.KEY_PAGEUP;
			case 267 -> InputConstants.KEY_PAGEDOWN;
			case 268 -> InputConstants.KEY_HOME;
			case 269 -> InputConstants.KEY_END;
			case 280 -> InputConstants.KEY_CAPSLOCK;
			case 281 -> InputConstants.KEY_SCROLLLOCK;
			case 282 -> InputConstants.KEY_NUMLOCK;
			case 283 -> InputConstants.KEY_PRINTSCREEN;
			case 284 -> InputConstants.KEY_PAUSE;
			case 330 -> SDLScancode.SDL_SCANCODE_KP_PERIOD;
			case 331 -> SDLScancode.SDL_SCANCODE_KP_DIVIDE;
			case 332 -> InputConstants.KEY_MULTIPLY;
			case 333 -> SDLScancode.SDL_SCANCODE_KP_MINUS;
			case 334 -> InputConstants.KEY_ADD;
			case 335 -> InputConstants.KEY_NUMPADENTER;
			case 336 -> InputConstants.KEY_NUMPADEQUALS;
			case 340 -> InputConstants.KEY_LSHIFT;
			case 341 -> InputConstants.KEY_LCONTROL;
			case 342 -> InputConstants.KEY_LALT;
			case 343 -> InputConstants.KEY_LGUI;
			case 344 -> InputConstants.KEY_RSHIFT;
			case 345 -> InputConstants.KEY_RCONTROL;
			case 346 -> InputConstants.KEY_RALT;
			case 347 -> InputConstants.KEY_RGUI;
			case 348 -> SDLScancode.SDL_SCANCODE_APPLICATION;
			default -> 0;
		};
	}
}
