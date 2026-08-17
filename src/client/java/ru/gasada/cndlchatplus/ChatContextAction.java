package ru.gasada.cndlchatplus;

public enum ChatContextAction {
	COPY_MESSAGE("Копировать сообщение"),
	COPY_NICK("Копировать ник"),
	PRIVATE_MESSAGE("Написать ЛС"),
	ADD_FRIEND("Добавить в друзья"),
	IGNORE("Игнорировать"),
	PLAYER_INFO("Информация об игроке"),
	PAY("Перевести деньги"),
	CALL("Запрос на ТП"),
	MAIL("Почта");

	private final String label;

	ChatContextAction(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
