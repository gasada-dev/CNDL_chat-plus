package ru.gasada.cndlchatplus;

enum ChatTabTextSize {
	SMALL(80),
	NORMAL(100),
	LARGE(120);

	private final int percent;

	ChatTabTextSize(int percent) {
		this.percent = percent;
	}

	int percent() {
		return percent;
	}

	static Integer parsePercent(String value) {
		String normalized = value == null ? "" : value.trim().replace("%", "");
		try {
			return Integer.valueOf(normalized);
		} catch (NumberFormatException error) {
			return null;
		}
	}
}
