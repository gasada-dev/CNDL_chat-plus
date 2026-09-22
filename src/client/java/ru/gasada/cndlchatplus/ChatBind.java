package ru.gasada.cndlchatplus;

import org.lwjgl.glfw.GLFW;

public final class ChatBind {
	public int keyCode;
	public boolean control;
	public boolean shift;
	public boolean alt;
	public String command;

	public ChatBind() {
		this(0, false, false, false, "");
	}

	public ChatBind(int keyCode, String command) {
		this(keyCode, false, false, false, command);
	}

	public ChatBind(int keyCode, boolean control, boolean shift, boolean alt, String command) {
		this.keyCode = keyCode;
		this.control = control;
		this.shift = shift;
		this.alt = alt;
		this.command = command;
	}

	boolean capture(int keyCode, int modifiers) {
		if (modifierKey(keyCode)) return false;
		this.keyCode = keyCode;
		control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
		return true;
	}

	boolean includes(ChatBind other) {
		return keyCode == other.keyCode && (!other.control || control) && (!other.shift || shift)
				&& (!other.alt || alt) && (control != other.control || shift != other.shift || alt != other.alt);
	}

	ChatBind copy() {
		return new ChatBind(keyCode, control, shift, alt, command);
	}

	private static boolean modifierKey(int keyCode) {
		return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
				|| keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
				|| keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT;
	}
}
