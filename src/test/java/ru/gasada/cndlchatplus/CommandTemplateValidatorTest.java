package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CommandTemplateValidatorTest {
	@Test
	void psKickRequiresOnlyPlayerPlaceholder() {
		CommandTemplateValidator.CommandType type = CommandTemplateValidator.CommandType.PS_KICK;
		assertTrue(CommandTemplateValidator.validate("pskick {player}", type).valid());
		assertTrue(CommandTemplateValidator.validate("custom kick {player}", type).valid());
		for (String template : new String[] { null, "", "pskick", "pskick {unknown}",
				"pskick {player} {amount}", "pskick {Player}", "/pskick {player}",
				"pskick {player}\nother" }) {
			assertFalse(CommandTemplateValidator.validate(template, type).valid());
		}
	}

	@Test
	void placeholderFreeCommandsAcceptPlainTemplatesAndRejectPlaceholders() {
		for (CommandTemplateValidator.CommandType type : new CommandTemplateValidator.CommandType[] {
				CommandTemplateValidator.CommandType.CLAIM_FLY,
				CommandTemplateValidator.CommandType.ENDER_CHEST,
				CommandTemplateValidator.CommandType.MARRY_KISS,
				CommandTemplateValidator.CommandType.MARRY_HOME,
				CommandTemplateValidator.CommandType.MARRY_TP }) {
			assertTrue(CommandTemplateValidator.validate("server command", type).valid());
			assertFalse(CommandTemplateValidator.validate("server command {player}", type).valid());
		}
	}
}
