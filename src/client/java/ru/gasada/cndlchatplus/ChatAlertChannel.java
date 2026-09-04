package ru.gasada.cndlchatplus;

public enum ChatAlertChannel {
	ANY("Любой"),
	GLOBAL("Глобал"),
	LOCAL("Локал"),
	CLAN("Клан"),
	PRIVATE("ЛС"),
	DISCORD("Discord"),
	SYSTEM("Система");

	private final String displayName;

	ChatAlertChannel(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	boolean matches(ChatTab tab) {
		return this == ANY || name().equals(tab.name());
	}
}
