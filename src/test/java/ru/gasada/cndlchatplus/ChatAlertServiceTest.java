package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ChatAlertServiceTest {
	@Test
	void matchesTextWildcardAndRegexWithoutSyntheticDecorations() {
		ChatAlertService service = service(List.of(
				rule("text", ChatAlertMatchType.TEXT, "gasada", ChatAlertChannel.ANY, 0),
				rule("wildcard", ChatAlertMatchType.WILDCARD, "*продам*алмазы*", ChatAlertChannel.ANY, 0),
				rule("regex", ChatAlertMatchType.REGEX, "привет\\s+мир", ChatAlertChannel.ANY, 0)));

		assertEquals(List.of("text"), service.handle("[12:34] [Steve head]Привет, Gasada!", ChatTab.LOCAL)
				.matchedRuleIds());
		assertEquals(List.of("wildcard"), service.handle("срочно продам 32 алмазы сегодня", ChatTab.GLOBAL)
				.matchedRuleIds());
		assertEquals(List.of("regex"), service.handle("ПРИВЕТ   МИР", ChatTab.PRIVATE).matchedRuleIds());
		assertFalse(service.handle("ничего подходящего", ChatTab.SYSTEM).triggered());
	}

	@Test
	void invalidRegexIsOmitted() {
		ChatAlertRule invalid = rule("invalid", ChatAlertMatchType.REGEX, "[", ChatAlertChannel.ANY, 0);
		assertTrue(new ChatAlertRuleCompiler().compile(List.of(invalid)).isEmpty());
		assertEquals("Некорректное регулярное выражение",
				new ChatAlertRuleCompiler().validationError(ChatAlertMatchType.REGEX, "["));
	}

	@Test
	void scopesEverySupportedChannelAndRejectsMismatch() {
		for (ChatTab tab : List.of(ChatTab.GLOBAL, ChatTab.LOCAL, ChatTab.CLAN, ChatTab.PRIVATE,
				ChatTab.DISCORD, ChatTab.SYSTEM)) {
			ChatAlertService service = service(List.of(
					rule("any", ChatAlertMatchType.TEXT, "match", ChatAlertChannel.ANY, 0),
					rule(tab.name(), ChatAlertMatchType.TEXT, "match", ChatAlertChannel.valueOf(tab.name()), 0)));
			assertEquals(List.of("any", tab.name()), service.handle("match", tab).matchedRuleIds());
		}
		ChatAlertService mismatch = service(List.of(
				rule("private", ChatAlertMatchType.TEXT, "match", ChatAlertChannel.PRIVATE, 0)));
		assertFalse(mismatch.handle("match", ChatTab.GLOBAL).triggered());
	}

	@Test
	void legacyCooldownValueDoesNotSuppressMessages() {
		ChatAlertService service = service(List.of(
				rule("rule", ChatAlertMatchType.TEXT, "match", ChatAlertChannel.ANY, 3600)));

		assertEquals(List.of("rule"), service.handle("match", ChatTab.LOCAL).matchedRuleIds());
		assertEquals(List.of("rule"), service.handle("match", ChatTab.LOCAL).matchedRuleIds());
	}

	@Test
	void aggregatesMultipleRulesIntoOneHudAndSoundAction() {
		ChatAlertRule sound = rule("sound", ChatAlertMatchType.TEXT, "match", ChatAlertChannel.ANY, 10);
		sound.hudEnabled = false;
		ChatAlertRule hud = rule("hud", ChatAlertMatchType.TEXT, "match", ChatAlertChannel.ANY, 10);
		hud.soundEnabled = false;
		ChatAlertService service = service(List.of(sound, hud));

		ChatAlertDecision first = service.handle("match", ChatTab.LOCAL);
		assertEquals(List.of("sound", "hud"), first.matchedRuleIds());
		assertTrue(first.soundEnabled());
		assertTrue(first.hudEnabled());

		ChatAlertDecision repeated = service.handle("match", ChatTab.LOCAL);
		assertEquals(List.of("sound", "hud"), repeated.matchedRuleIds());
		assertTrue(repeated.soundEnabled());
		assertTrue(repeated.hudEnabled());
	}

	@Test
	void globalDisableSuppressesAllActions() {
		ChatAlertService service = new ChatAlertService(() -> false,
				List.of(rule("one", ChatAlertMatchType.TEXT, "match", ChatAlertChannel.ANY, 0)));
		assertFalse(service.handle("match", ChatTab.LOCAL).triggered());
	}

	@Test
	void doesNotMatchRecognizedSenderName() {
		ChatAlertService service = service(List.of(
				rule("name", ChatAlertMatchType.TEXT, "gasada", ChatAlertChannel.ANY, 0)));
		CompiledParserSettings parsers = CompiledParserSettings.compile(ParserSettings.vanillaBoxDefaults());

		assertFalse(service.handle("(!) gasada » обычный текст", ChatTab.GLOBAL, parsers).triggered());
		assertTrue(service.handle("(!) Steve » привет gasada", ChatTab.GLOBAL, parsers).triggered());
	}

	private ChatAlertService service(List<ChatAlertRule> rules) {
		return new ChatAlertService(() -> true, rules);
	}

	private static ChatAlertRule rule(String id, ChatAlertMatchType type, String pattern,
			ChatAlertChannel channel, int cooldown) {
		return new ChatAlertRule(id, id, true, type, pattern, channel, true, true, cooldown);
	}
}
