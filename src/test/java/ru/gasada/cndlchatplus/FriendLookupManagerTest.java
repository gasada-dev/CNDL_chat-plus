package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class FriendLookupManagerTest {
	@Test
	void automaticallyQueuesFriendsAfterConnectionDelay() {
		TestContext context = context("Alice");

		context.lookup.tick(true);
		context.clock.set(29_999);
		context.lookup.tick(true);
		assertTrue(context.commands.isEmpty());

		context.clock.set(30_000);
		context.lookup.tick(true);
		assertEquals(List.of("clan lookup Alice"), context.commands);
	}

	@Test
	void openingFriendsQueuesEarlyWithoutAutomaticDuplicates() {
		TestContext context = context("Alice", "Bob");
		context.lookup.tick(true);

		context.lookup.queueActiveFriends();
		context.lookup.queueActiveFriends();
		context.lookup.tick(true);
		assertEquals(List.of("clan lookup Alice"), context.commands);
		context.completeResponse();

		context.clock.set(10_000);
		context.lookup.tick(true);
		assertEquals(List.of("clan lookup Alice", "clan lookup Bob"), context.commands);
		context.completeResponse();

		context.clock.set(30_000);
		context.lookup.queueActiveFriends();
		context.lookup.tick(true);
		assertEquals(2, context.commands.size());
	}

	@Test
	void pausesAfterFiveSequentialLookups() {
		TestContext context = context("One", "Two", "Three", "Four", "Five", "Six");
		context.lookup.queueActiveFriends();

		for (int index = 0; index < 5; index++) {
			context.lookup.tick(true);
			context.completeResponse();
			if (index < 4) context.clock.addAndGet(10_000);
		}
		assertEquals(List.of(
				"clan lookup One", "clan lookup Two", "clan lookup Three",
				"clan lookup Four", "clan lookup Five"), context.commands);

		context.clock.addAndGet(59_999);
		context.lookup.tick(true);
		assertEquals(5, context.commands.size());
		context.clock.incrementAndGet();
		context.lookup.tick(true);
		assertEquals("clan lookup Six", context.commands.getLast());
	}

	@Test
	void retriesTimedOutFriendOnceBeforeContinuingInOrder() {
		TestContext context = context("Alice", "Bob");
		context.lookup.queueActiveFriends();
		context.lookup.tick(true);

		context.clock.set(15_000);
		context.lookup.tick(true);
		context.clock.set(74_999);
		context.lookup.tick(true);
		assertEquals(List.of("clan lookup Alice"), context.commands);

		context.clock.set(75_000);
		context.lookup.tick(true);
		assertEquals(List.of("clan lookup Alice", "clan lookup Alice"), context.commands);

		context.clock.set(90_000);
		context.lookup.tick(true);
		context.clock.set(150_000);
		context.lookup.tick(true);
		assertEquals(List.of("clan lookup Alice", "clan lookup Alice", "clan lookup Bob"),
				context.commands);
	}

	@Test
	void manualLookupRunsBeforeQueuedBackgroundFriends() {
		TestContext context = context("Alice", "Bob");
		context.lookup.queueActiveFriends();

		assertTrue(context.lookup.queueManualLookup("ManualOne", ignored -> { }));
		assertTrue(context.lookup.queueManualLookup("ManualTwo", ignored -> { }));
		context.lookup.tick(true);
		assertEquals(List.of("clan lookup ManualOne"), context.commands);
		context.completeResponse();

		context.clock.set(10_000);
		context.lookup.tick(true);
		assertEquals("clan lookup ManualTwo", context.commands.getLast());
		context.completeResponse();

		context.clock.set(20_000);
		context.lookup.tick(true);
		assertEquals("clan lookup Alice", context.commands.getLast());
	}

	@Test
	void disconnectClearsQueueAndRestartsAutomaticDelay() {
		TestContext context = context("Alice");
		context.lookup.queueActiveFriends();
		context.lookup.tick(true);
		context.lookup.tick(false);

		context.clock.set(100_000);
		context.lookup.tick(true);
		context.clock.set(129_999);
		context.lookup.tick(true);
		assertEquals(1, context.commands.size());
		context.clock.set(130_000);
		context.lookup.tick(true);
		assertEquals(2, context.commands.size());
		assertTrue(context.lookup.shouldShowSystemMessage(Component.literal("Обычное сообщение"), false));
	}

	private static TestContext context(String... friends) {
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		template.friends.addAll(List.of(friends));
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(template);
		AtomicLong clock = new AtomicLong();
		RecordingTransport transport = new RecordingTransport();
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(transport, ignored -> { }));
		FriendLookupManager lookup = new FriendLookupManager(runtime,
				new FriendActionService(runtime, commands, null), clock::get);
		return new TestContext(lookup, clock, transport.commands);
	}

	private record TestContext(FriendLookupManager lookup, AtomicLong clock, List<String> commands) {
		private void completeResponse() {
			lookup.shouldShowSystemMessage(Component.literal("Тип убийства: обычный"), false);
		}
	}

	private static final class RecordingTransport implements OutgoingChatService.Transport {
		private final List<String> commands = new ArrayList<>();

		@Override public boolean connected() { return true; }
		@Override public void execute(Runnable action) { action.run(); }
		@Override public void sendChat(String message) { }
		@Override public void sendCommand(String command) { commands.add(command); }
	}
}
