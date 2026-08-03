package ru.gasada.chatresponder;

public final class CommandTemplateDisplay {
	private CommandTemplateDisplay() {
	}

	public static String format(String template) {
		if (template == null || template.isBlank()) {
			return "команда не настроена";
		}
		String value = template.trim();
		if (!value.startsWith("/")) value = "/" + value;
		return value.replace("{player}", "ник")
				.replace("{message}", "сообщение")
				.replace("{amount}", "сумма");
	}
}
