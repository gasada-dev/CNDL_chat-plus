package ru.gasada.cndlchatplus;

public enum ChatTab {
	ALL("Все"),
	GLOBAL("Глобал"),
	LOCAL("Локал"),
	CLAN("Клан"),
	PRIVATE("ЛС"),
	DISCORD("Discord"),
	SYSTEM("Система");

	private final String displayName;

	ChatTab(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
