package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
}
