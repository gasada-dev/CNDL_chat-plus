package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

final class ChatResponderComponentsTest {
	@Test
	void duplicateGuardUsesStrictExistingWindowAndCanReset() {
		AtomicLong now = new AtomicLong(1_000);
		DuplicateMessageGuard guard = new DuplicateMessageGuard(400, now::get);
		assertFalse(guard.isDuplicate(" Hello ", "Display"));
		now.addAndGet(399);
		assertTrue(guard.isDuplicate("hello", "display"));
		now.incrementAndGet();
		assertFalse(guard.isDuplicate("HELLO", "DISPLAY"));
		guard.reset();
		assertFalse(guard.isDuplicate("hello", "display"));
	}

	@Test
	void ownGuardRecognizesEchoUntilFiveSecondBoundary() {
		AtomicLong now = new AtomicLong(10_000);
		OwnMessageGuard guard = new OwnMessageGuard(5_000, now::get);
		ActiveTemplateSnapshot template = snapshot("one", "!", "/.");
		guard.recordOutgoing("!Ответ");
		assertTrue(guard.isRecentEcho("Ответ", "Player: Ответ", template));
		now.addAndGet(4_999);
		assertTrue(guard.isRecentEcho("Ответ", "Ответ", template));
		now.incrementAndGet();
		assertFalse(guard.isRecentEcho("Ответ", "Ответ", template));
	}

	@Test
	void ownDisplayedDetectionPreservesSupportedDecorationsAndNameBoundary() {
		assertTrue(OwnMessageGuard.isLikelyOwnDisplayedMessage("[G] My_Name » hello", "my_name"));
		assertTrue(OwnMessageGuard.isLikelyOwnDisplayedMessage("〈My_Name〉 hello", "my_name"));
		assertTrue(OwnMessageGuard.isLikelyOwnDisplayedMessage("[12:00] My_Name: hello", "my_name"));
		assertFalse(OwnMessageGuard.isLikelyOwnDisplayedMessage("OtherMy_Name » hello", "my_name"));
	}

	@Test
	void candidateBuilderPreservesOrderDedupAndActiveSeparators() {
		ServerTemplate template = LegacyConfigToVanillaBoxMigration.fromLegacy(new ResponderConfig());
		template.parsers.replyCandidateSeparators = List.of(" :: ");
		ActiveTemplateSnapshot snapshot = ActiveTemplateSnapshot.from(template, 1);
		List<String> candidates = new ReplyCandidateBuilder().build(
				"!Привет", "[world] User :: Привет", ChatChannel.GLOBAL, snapshot,
				CompiledParserSettings.compile(snapshot.parsers()));
		assertEquals(List.of("!привет", "[world] user :: привет", "привет"), candidates);
	}

	@Test
	void compiledRuleMatcherUsesFirstEnabledMatchingRule() {
		List<ActiveTemplateSnapshot.RuleSnapshot> rules = List.of(
				new ActiveTemplateSnapshot.RuleSnapshot(false, "*", "disabled", ChatChannel.AUTO),
				new ActiveTemplateSnapshot.RuleSnapshot(true, "привет*", "first", ChatChannel.AUTO),
				new ActiveTemplateSnapshot.RuleSnapshot(true, "*", "second", ChatChannel.LOCAL));
		ActiveTemplateSnapshot.RuleSnapshot matched = ReplyRuleMatcher.compile(rules)
				.findFirst(ChatChannel.LOCAL, List.of("привет мир"));
		assertEquals("first", matched.response());
		assertNull(ReplyRuleMatcher.compile(rules).findFirst(ChatChannel.GLOBAL, List.of("нет")));
	}

	@Test
	void switchingRuntimeReplacesCompiledRules() {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate first = ServerTemplate.empty("one", "One");
		first.rules.add(new ReplyRule("alpha", "one", ChatChannel.AUTO));
		ServerTemplate second = ServerTemplate.empty("two", "Two");
		second.rules.add(new ReplyRule("beta", "two", ChatChannel.AUTO));
		runtime.switchTo(first);
		assertEquals("one", runtime.compiledReplyRules().orElseThrow()
				.findFirst(ChatChannel.LOCAL, List.of("alpha")).response());
		runtime.switchTo(second);
		assertNull(runtime.compiledReplyRules().orElseThrow()
				.findFirst(ChatChannel.LOCAL, List.of("alpha")));
		assertEquals("two", runtime.compiledReplyRules().orElseThrow()
				.findFirst(ChatChannel.LOCAL, List.of("beta")).response());
	}

	private static ActiveTemplateSnapshot snapshot(String id, String global, String clan) {
		ServerTemplate template = ServerTemplate.empty(id, id);
		template.globalPrefix = global;
		template.clanReplyPrefix = clan;
		return ActiveTemplateSnapshot.from(template, 1);
	}
}
