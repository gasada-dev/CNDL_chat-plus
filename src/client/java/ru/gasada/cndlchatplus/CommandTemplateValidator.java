package ru.gasada.cndlchatplus;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandTemplateValidator {
	public static final int MAX_LENGTH = 128;
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)}");

	private CommandTemplateValidator() {
	}

	public static ValidationResult validate(String template, CommandType type) {
		InputSanitizer.Result sanitized = InputSanitizer.validateAndTrim(template, MAX_LENGTH, "Шаблон команды");
		if (!sanitized.valid()) {
			return ValidationResult.failure(sanitized.errorMessage());
		}
		if (sanitized.value().startsWith("/")) {
			return ValidationResult.failure("Шаблон команды хранится без начального /");
		}

		Set<String> found = new LinkedHashSet<>();
		Matcher matcher = PLACEHOLDER.matcher(sanitized.value());
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		if (!found.equals(type.requiredPlaceholders())) {
			return ValidationResult.failure("Неверный набор placeholders для команды " + type);
		}
		String withoutKnown = PLACEHOLDER.matcher(sanitized.value()).replaceAll("");
		if (withoutKnown.contains("{") || withoutKnown.contains("}")) {
			return ValidationResult.failure("Некорректный placeholder в шаблоне команды");
		}
		return ValidationResult.success(sanitized.value());
	}

	public enum CommandType {
		IGNORE_PLAYER(Set.of("player")),
		LOOKUP_FRIEND(Set.of("player")),
		PRIVATE_MESSAGE(Set.of("player", "message")),
		PAY(Set.of("player", "amount")),
		CALL(Set.of("player")),
		MAIL(Set.of("player", "message")),
		MARRIAGE_LIST(Set.of("page"));

		private final Set<String> requiredPlaceholders;

		CommandType(Set<String> requiredPlaceholders) {
			this.requiredPlaceholders = requiredPlaceholders;
		}

		Set<String> requiredPlaceholders() {
			return requiredPlaceholders;
		}
	}

	public record ValidationResult(boolean valid, String normalizedTemplate, String errorMessage) {
		private static ValidationResult success(String template) {
			return new ValidationResult(true, template, "");
		}

		private static ValidationResult failure(String errorMessage) {
			return new ValidationResult(false, null, errorMessage);
		}
	}
}
