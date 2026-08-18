package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class ServerTemplateRuntimeTest {
	@Test
	void switchPublishesImmutableDeepSnapshotAndResetsEveryState() {
		AtomicInteger resets = new AtomicInteger();
		TemplateSwitchCoordinator coordinator = new TemplateSwitchCoordinator();
		coordinator.register(resets::incrementAndGet);
		coordinator.register(resets::incrementAndGet);
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(coordinator);

		ServerTemplate first = template("first", "Alice", "one");
		ActiveTemplateSnapshot snapshot = runtime.switchTo(first);
		first.friends.add("MutatedAfterSwitch");
		assertEquals(2, resets.get());
		assertEquals("Alice", snapshot.friends().getFirst());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.friends().add("bad"));
	}

	@Test
	void switchingTemplatesDoesNotLeakTemplateState() {
		AtomicInteger resets = new AtomicInteger();
		TemplateSwitchCoordinator coordinator = new TemplateSwitchCoordinator();
		coordinator.register(resets::incrementAndGet);
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(coordinator);
		ActiveTemplateSnapshot first = runtime.switchTo(template("first", "Alice", "one"));

		ActiveTemplateSnapshot second = runtime.switchTo(template("second", "Bob", "two"));
		assertEquals(2, resets.get());
		assertTrue(second.generation() > first.generation());
		assertEquals("second", second.id());
		assertEquals("Bob", second.friends().getFirst());
		assertFalse(second.friends().contains("Alice"));
	}

	@Test
	void clearResetsRuntimeAndRemovesActiveSnapshot() {
		AtomicInteger resets = new AtomicInteger();
		TemplateSwitchCoordinator coordinator = new TemplateSwitchCoordinator();
		coordinator.register(resets::incrementAndGet);
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(coordinator);
		runtime.switchTo(template("first", "Alice", "one"));
		runtime.clear();
		assertEquals(2, resets.get());
		assertTrue(runtime.activeSnapshot().isEmpty());
	}

	@Test
	void runtimeIgnoresNullableAutomationBridge() {
		ServerTemplate template = ServerTemplate.empty("nullable", "Nullable");
		template.rules = null;
		template.periodicMessages = null;
		template.clanReplyPrefix = null;
		template.privateReplyCommand = null;
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());

		ActiveTemplateSnapshot snapshot = runtime.switchTo(template);

		assertEquals("nullable", snapshot.id());
		assertTrue(runtime.compiledParsers().isPresent());
		assertTrue(runtime.compiledFilters().isPresent());
	}

	private static ServerTemplate template(String id, String friend, String trigger) {
		ServerTemplate template = ServerTemplate.empty(id, id);
		template.friends.add(friend);
		template.friendLastSeen.put(friend, id + " seen");
		template.rules.add(new ReplyRule(trigger, "reply", ChatChannel.AUTO));
		template.mutedWords.add(id + " muted");
		template.periodicMessages.add(new PeriodicMessageConfig(true, id + " periodic", 5));
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		return template;
	}
}
