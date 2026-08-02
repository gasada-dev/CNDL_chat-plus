package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.List;

public final class TemplateSettingsValidator {
	private TemplateSettingsValidator() {
	}

	public static List<String> validate(ServerTemplate template) {
		List<String> errors = new ArrayList<>();
		if (template == null) {
			return List.of("Шаблон не задан");
		}
		validateCommand(errors, template.commands.ignorePlayer,
				CommandTemplateValidator.CommandType.IGNORE_PLAYER);
		validateCommand(errors, template.commands.lookupFriend,
				CommandTemplateValidator.CommandType.LOOKUP_FRIEND);
		validateCommand(errors, template.commands.privateMessage,
				CommandTemplateValidator.CommandType.PRIVATE_MESSAGE);
		validateCommand(errors, template.commands.pay, CommandTemplateValidator.CommandType.PAY);
		validateCommand(errors, template.commands.call, CommandTemplateValidator.CommandType.CALL);
		validateCommand(errors, template.commands.mail, CommandTemplateValidator.CommandType.MAIL);

		ParserSettings parsers = template.parsers;
		validatePattern(errors, "Discord marker", parsers.discordMarkerPattern, false);
		validatePattern(errors, "Discord name", parsers.discordNamePattern, false);
		validatePattern(errors, "Last seen", parsers.lastSeenPattern, true);
		validatePattern(errors, "Inactive", parsers.inactivePattern, true);
		validatePattern(errors, "Lookup end", parsers.lookupEndPattern, false);
		validatePattern(errors, "Lookup output", parsers.lookupOutputPattern, false);
		validatePattern(errors, "Timestamp", parsers.timestampOnlyPattern, false);
		return errors;
	}

	private static void validateCommand(List<String> errors, String value,
			CommandTemplateValidator.CommandType type) {
		if (value == null || value.isBlank()) return;
		CommandTemplateValidator.ValidationResult result = CommandTemplateValidator.validate(value, type);
		if (!result.valid()) errors.add(type + ": " + result.errorMessage());
	}

	private static void validatePattern(List<String> errors, String label, String value, boolean capture) {
		if (value == null || value.isBlank()) return;
		ParserPatternValidator.ValidationResult result = ParserPatternValidator.validate(value, capture);
		if (!result.valid()) errors.add(label + ": " + result.errorMessage());
	}
}
