package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CommandTemplateDisplayTest {
	@Test
	void activeCommandTemplateIsRenderedAsUserFacingHint() {
		assertEquals("/w ник сообщение", CommandTemplateDisplay.format("w {player} {message}"));
		assertEquals("/tpa ник", CommandTemplateDisplay.format("tpa {player}"));
		assertEquals("/pay ник сумма", CommandTemplateDisplay.format("pay {player} {amount}"));
	}

	@Test
	void missingCommandHasExplicitHint() {
		assertEquals("команда не настроена", CommandTemplateDisplay.format(""));
		assertEquals("команда не настроена", CommandTemplateDisplay.format(null));
	}
}
