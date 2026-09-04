package ru.gasada.cndlchatplus;

public enum ChatAlertMatchType {
	TEXT("Текст"),
	WILDCARD("Маска"),
	REGEX("Regex");

	private final String displayName;

	ChatAlertMatchType(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
