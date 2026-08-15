package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class FriendTemplateIsolationTest {
	@Test
	void lastSeenUpdateAffectsOnlyActiveTemplateSnapshot() {
		ServerTemplate first = ServerTemplate.empty("first", "First");
		first.friends.add("Alice");
		ServerTemplate second = ServerTemplate.empty("second", "Second");
		second.friends.add("Bob");
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(first);
		FriendActionService actions = new FriendActionService(runtime, null, null);

		assertTrue(actions.updateLastSeen("alice", "вчера"));
		assertEquals("вчера", runtime.activeSnapshot().orElseThrow().friendLastSeen().get("Alice"));
		runtime.switchTo(second);
		assertFalse(actions.updateLastSeen("Alice", "сегодня"));
		assertTrue(runtime.activeSnapshot().orElseThrow().friendLastSeen().isEmpty());
	}

	@Test
	void onlyVanillaBoxLastSeenSavePreservesLookupQueue() {
		assertTrue(FriendActionService.usesQueuePreservingSave("vanilla-box"));
		assertFalse(FriendActionService.usesQueuePreservingSave("vanilla-game"));
		assertFalse(FriendActionService.usesQueuePreservingSave(null));
	}

	@Test
	void lookupQueueAcceptsOnlyFriendsFromActiveTemplateAndClearsOnSwitchHook() {
		TemplateSwitchCoordinator coordinator = new TemplateSwitchCoordinator();
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(coordinator);
		ServerTemplate first = ServerTemplate.empty("first", "First");
		first.friends.add("Alice");
		runtime.switchTo(first);
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, null, null), System::currentTimeMillis);
		coordinator.register(lookup::resetRuntimeState);

		lookup.queueFriends(List.of("Alice", "Bob", "alice"));
		assertEquals(1, lookup.queuedCount());
		ServerTemplate second = ServerTemplate.empty("second", "Second");
		second.friends.add("Bob");
		runtime.switchTo(second);
		assertEquals(0, lookup.queuedCount());
		lookup.queueActiveFriends();
		assertEquals(1, lookup.queuedCount());
	}

	@Test
	void manualLookupSharesQueueAndCompletesWhenTemplateSwitchResetsIt() {
		TemplateSwitchCoordinator coordinator = new TemplateSwitchCoordinator();
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(coordinator);
		runtime.switchTo(ServerTemplate.empty("first", "First"));
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, null, null), System::currentTimeMillis);
		coordinator.register(lookup::resetRuntimeState);
		AtomicBoolean completed = new AtomicBoolean();

		assertTrue(lookup.queueManualLookup("Player_1", ignored -> completed.set(true)));
		assertEquals(1, lookup.queuedCount());
		assertFalse(lookup.queueManualLookup("player_1", ignored -> { }));
		runtime.switchTo(ServerTemplate.empty("second", "Second"));
		assertTrue(completed.get());
		assertEquals(0, lookup.queuedCount());
	}

	@Test
	void manualLookupOutputRemainsVisibleWithoutPendingModRequest() {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.switchTo(template);
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, null, null), System::currentTimeMillis);

		assertTrue(lookup.shouldShowSystemMessage(Component.literal("Клан: Builders"), false));
	}
}
