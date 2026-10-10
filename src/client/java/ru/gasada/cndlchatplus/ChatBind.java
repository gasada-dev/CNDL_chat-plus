package ru.gasada.cndlchatplus;

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
		if (PlatformInput.modifierKey(keyCode)) return false;
		int storedKey = PlatformInput.storageKey(keyCode);
		if (storedKey <= 0) return false;
		this.keyCode = storedKey;
		control = PlatformInput.control(modifiers);
		shift = PlatformInput.shift(modifiers);
		alt = PlatformInput.alt(modifiers);
		return true;
	}

	boolean includes(ChatBind other) {
		return keyCode == other.keyCode && (!other.control || control) && (!other.shift || shift)
				&& (!other.alt || alt) && (control != other.control || shift != other.shift || alt != other.alt);
	}

	ChatBind copy() {
		return new ChatBind(keyCode, control, shift, alt, command);
	}

}
