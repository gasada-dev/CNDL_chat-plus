package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ChatResponderEngineTest {
	@Test
	void firstMatchingEnabledRuleWins() {
		ReplyRule first = new ReplyRule("привет*", "первый", ChatChannel.AUTO);
		ReplyRule second = new ReplyRule("*", "второй", ChatChannel.AUTO);

		ReplyRule matched = ChatResponderEngine.findFirstMatchingRule(
				List.of(first, second), ChatChannel.LOCAL, List.of("привет всем"));

		assertSame(first, matched);
	}

	@Test
	void disabledRuleIsSkippedBeforeNextMatch() {
		ReplyRule disabled = new ReplyRule("*", "первый", ChatChannel.AUTO);
		disabled.enabled = false;
		ReplyRule enabled = new ReplyRule("*", "второй", ChatChannel.AUTO);

		assertSame(enabled, ChatResponderEngine.findFirstMatchingRule(
				List.of(disabled, enabled), ChatChannel.LOCAL, List.of("текст")));
	}

	@Test
	void blankTriggerAndResponseAreSkipped() {
		ReplyRule blankTrigger = new ReplyRule(" ", "ответ", ChatChannel.AUTO);
		ReplyRule blankResponse = new ReplyRule("*", " ", ChatChannel.AUTO);
		ReplyRule valid = new ReplyRule("*", "ответ", ChatChannel.AUTO);

		assertSame(valid, ChatResponderEngine.findFirstMatchingRule(
				List.of(blankTrigger, blankResponse, valid), ChatChannel.LOCAL, List.of("текст")));
	}

	@Test
	void explicitChannelMustMatchDetectedChannel() {
		ReplyRule global = new ReplyRule("*", "global", ChatChannel.GLOBAL);
		ReplyRule local = new ReplyRule("*", "local", ChatChannel.LOCAL);

		assertSame(local, ChatResponderEngine.findFirstMatchingRule(
				List.of(global, local), ChatChannel.LOCAL, List.of("текст")));
	}

	@Test
	void noApplicableRuleReturnsNull() {
		ReplyRule rule = new ReplyRule("точно", "ответ", ChatChannel.LOCAL);

		assertNull(ChatResponderEngine.findFirstMatchingRule(
				List.of(rule), ChatChannel.GLOBAL, List.of("другое")));
	}
}
