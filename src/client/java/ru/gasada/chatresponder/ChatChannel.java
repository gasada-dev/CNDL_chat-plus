package ru.gasada.chatresponder;

public enum ChatChannel {
	AUTO("Авто"),
	LOCAL("Локальный"),
	GLOBAL("Глобальный"),
	CLAN("Клановый"),
	PRIVATE("Личный");

	private final String displayName;

	ChatChannel(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
