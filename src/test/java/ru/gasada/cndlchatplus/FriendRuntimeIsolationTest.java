package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class FriendRuntimeIsolationTest {
	@Test
	void lastSeenUpdateAffectsOnlyActiveVanillaBoxSnapshot() {
		VanillaBoxConfig first = VanillaBoxConfig.empty();
		first.friends.add("Alice");
		VanillaBoxConfig second = VanillaBoxConfig.empty();
		second.friends.add("Bob");
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(first);
		FriendActionService actions = new FriendActionService(runtime, null, null);

		assertTrue(actions.updateLastSeen("alice", "вчера"));
		assertEquals("вчера", runtime.activeSnapshot().orElseThrow().friendLastSeen().get("Alice"));
		runtime.activate(second);
		assertFalse(actions.updateLastSeen("Alice", "сегодня"));
		assertTrue(runtime.activeSnapshot().orElseThrow().friendLastSeen().isEmpty());
	}

	@Test
	void lastSeenUpdatePreservesLookupQueue() {
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.friends.addAll(List.of("Alice", "Bob"));
		runtime.activate(config);
		FriendActionService actions = new FriendActionService(runtime, null, null);
		FriendLookupManager lookup = new FriendLookupManager(runtime, actions, System::currentTimeMillis);
		coordinator.register(lookup::resetRuntimeState);
		lookup.queueFriends(config.friends);

		assertTrue(actions.updateLastSeen("alice", "вчера"));

		assertEquals(2, lookup.queuedCount());
		assertEquals("вчера", runtime.activeSnapshot().orElseThrow().friendLastSeen().get("Alice"));
	}

	@Test
	void lookupQueueAcceptsOnlyConfiguredFriendsAndClearsOnRuntimeReset() {
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);
		VanillaBoxConfig first = VanillaBoxConfig.empty();
		first.friends.add("Alice");
		runtime.activate(first);
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, null, null), System::currentTimeMillis);
		coordinator.register(lookup::resetRuntimeState);

		lookup.queueFriends(List.of("Alice", "Bob", "alice"));
		assertEquals(1, lookup.queuedCount());
		VanillaBoxConfig second = VanillaBoxConfig.empty();
		second.friends.add("Bob");
		runtime.activate(second);
		assertEquals(0, lookup.queuedCount());
		lookup.queueActiveFriends();
		assertEquals(1, lookup.queuedCount());
	}

	@Test
	void manualLookupSharesQueueAndCompletesWhenRuntimeResets() {
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);
		runtime.activate(VanillaBoxConfig.empty());
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, null, null), System::currentTimeMillis);
		coordinator.register(lookup::resetRuntimeState);
		AtomicBoolean completed = new AtomicBoolean();

		assertTrue(lookup.queueManualLookup("Player_1", ignored -> completed.set(true)));
		assertEquals(1, lookup.queuedCount());
		assertFalse(lookup.queueManualLookup("player_1", ignored -> { }));
		runtime.activate(VanillaBoxConfig.empty());
		assertTrue(completed.get());
		assertEquals(0, lookup.queuedCount());
	}

	@Test
	void manualLookupOutputRemainsVisibleWithoutPendingModRequest() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.activate(config);
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, null, null), System::currentTimeMillis);

		assertTrue(lookup.shouldShowSystemMessage(Component.literal("Клан: Builders"), false));
	}

	@Test
	void trailingBlankLookupLinesRemainHiddenAfterLookupEnd() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		config.friends.add("Player_1");
		runtime.activate(config);
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(new ConnectedTransport(), ignored -> { }));
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, commands, null), () -> 1L);

		lookup.queueActiveFriends();
		lookup.tick(true);

		assertFalse(lookup.shouldShowSystemMessage(Component.literal("Тип убийства: обычный"), false));
		assertFalse(lookup.shouldShowSystemMessage(Component.literal(""), false));
		assertFalse(lookup.shouldShowSystemMessage(Component.literal("   "), false));
		assertTrue(lookup.shouldShowSystemMessage(Component.literal("Обычное сообщение"), false));
		assertTrue(lookup.shouldShowSystemMessage(Component.literal(""), false));
	}

	private static final class ConnectedTransport implements OutgoingChatService.Transport {
		@Override
		public boolean connected() { return true; }

		@Override
		public void execute(Runnable action) { action.run(); }

		@Override
		public void sendChat(String message) { }

		@Override
		public void sendCommand(String command) { }
	}
}
