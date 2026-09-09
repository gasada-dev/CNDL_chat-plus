package ru.gasada.cndlchatplus;

enum MarriageAction {
	KISS("Поцеловать"),
	HOME("Тп домой"),
	TP("Тп к любви");

	private final String label;

	MarriageAction(String label) {
		this.label = label;
	}

	String label() {
		return label;
	}
}
