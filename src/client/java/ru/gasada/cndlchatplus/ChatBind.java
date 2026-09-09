package ru.gasada.cndlchatplus;

public final class ChatBind {
	public int keyCode;
	public String command;

	public ChatBind() {
		this(0, "");
	}

	public ChatBind(int keyCode, String command) {
		this.keyCode = keyCode;
		this.command = command;
	}

	ChatBind copy() {
		return new ChatBind(keyCode, command);
	}
}
