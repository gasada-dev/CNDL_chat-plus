package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class TeleportRequestButtonTest {
	@Test
	void matchesConfiguredRequestAndExpiresAfterSixtySeconds() {
		long[] now = {1_000L};
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands.acceptTeleport = "tpaccept";
		ParserSettings.applyTeleportDefaults(config.parsers);
		runtime.activate(config);
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(new ConnectedTransport(), ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(runtime, commands, () -> now[0]);

		button.handleMessage("[Player head] meowfedron просит телепортироваться к вам.");
		assertTrue(button.visible());
		assertEquals("meowfedron", button.requester());

		now[0] += TeleportRequestButton.TIMEOUT_MILLIS;
		assertFalse(button.visible());
	}

	@Test
	void ignoresMessagesWithoutRuntimeConfigAndResetsOnReplacement() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		runtime.activate(config);
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(new ConnectedTransport(), ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(runtime, commands, () -> 1_000L);

		button.handleMessage("Player просит телепортироваться к вам.");
		assertFalse(button.visible());

		config.commands.acceptTeleport = "tpaccept";
		ParserSettings.applyTeleportDefaults(config.parsers);
		runtime.activate(config);
		button.handleMessage("Player просит телепортироваться к вам.");
		assertTrue(button.visible());
		runtime.clear();
		assertFalse(button.visible());
	}

	@Test
	void schedulesOneShulkerSoundPerRequest() {
		AtomicInteger sounds = new AtomicInteger();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.activate(config);
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(new ConnectedTransport(), ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(
				runtime, commands, () -> 1_000L, sounds::incrementAndGet);

		button.handleMessage("Player просит телепортироваться к вам.");
		button.playPendingSound();
		button.playPendingSound();

		assertEquals(1, sounds.get());
	}

	@Test
	void disabledRequestSoundKeepsManualButton() {
		AtomicInteger sounds = new AtomicInteger();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.activate(config);
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(new ConnectedTransport(), ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(
				runtime, commands, () -> 1_000L, sounds::incrementAndGet, () -> false);

		button.handleMessage("Player просит телепортироваться к вам.");
		button.playPendingSound();

		assertTrue(button.visible());
		assertEquals(0, sounds.get());
	}

	@Test
	void automaticallyAcceptsRequestsAllowedByEachModeWithoutShowingButton() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		config.friends.addAll(List.of("Alice", "Bob"));
		config.teleportAutoAcceptMode = TeleportAutoAcceptMode.SELECTED_FRIENDS;
		config.teleportAutoAcceptFriends.add("Alice");
		runtime.activate(config);
		ConnectedTransport transport = new ConnectedTransport();
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(transport, ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(runtime, commands, () -> 1_000L);

		button.handleMessage("Bob просит телепортироваться к вам.");
		assertTrue(button.visible());
		assertTrue(transport.commands.isEmpty());

		button.handleMessage("alice просит телепортироваться к вам.");
		assertEquals(List.of("Bob"), button.visibleRequesters());
		assertEquals(List.of("tpaccept alice"), transport.commands);

		config.teleportAutoAcceptMode = TeleportAutoAcceptMode.FRIENDS;
		runtime.activate(config);
		button.handleMessage("Bob просит телепортироваться к вам.");
		assertFalse(button.visible());
		assertEquals(2, transport.commands.size());

		config.teleportAutoAcceptMode = TeleportAutoAcceptMode.EVERYONE;
		runtime.activate(config);
		button.handleMessage("Stranger просит телепортироваться к вам.");
		assertFalse(button.visible());
		assertEquals(3, transport.commands.size());
	}

	@Test
	void queuesFiveShowsThreeAndAcceptsSpecificPlayerWithoutLosingOthers() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.activate(config);
		ConnectedTransport transport = new ConnectedTransport();
		long[] now = {1_000L};
		TeleportRequestButton button = new TeleportRequestButton(runtime,
				new ServerCommandService(runtime, new OutgoingChatService(transport, ignored -> { })), () -> now[0]);
		for (String player : List.of("One", "Two", "Three", "Four", "Five")) {
			button.handleMessage(player + " просит телепортироваться к вам.");
		}
		assertEquals(List.of("One", "Two", "Three"), button.visibleRequesters());
		runtime.updateLastSeen("Alice", "сейчас");
		assertEquals(List.of("One", "Two", "Three"), button.visibleRequesters());
		assertTrue(button.accept("Two"));
		assertEquals(List.of("tpaccept Two"), transport.commands);
		assertEquals(List.of("One", "Three", "Four"), button.visibleRequesters());
		transport.connected = false;
		assertFalse(button.accept("Three"));
		assertEquals(List.of("One", "Three", "Four"), button.visibleRequesters());
		transport.connected = true;
		now[0] += 30_000;
		button.handleMessage("one просит телепортироваться к вам.");
		assertEquals(List.of("one", "Three", "Four"), button.visibleRequesters());
		now[0] += 30_000;
		assertEquals(List.of("one"), button.visibleRequesters());
		runtime.clear();
		assertFalse(button.visible());
	}

	@Test
	void keepsCardUntilTransportSendsAndOnDeferredFailure() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.activate(config);
		List<Runnable> queued = new ArrayList<>();
		List<String> sent = new ArrayList<>();
		boolean[] connected = {true};
		OutgoingChatService.Transport transport = new OutgoingChatService.Transport() {
			@Override public boolean connected() { return connected[0]; }
			@Override public void execute(Runnable action) { queued.add(action); }
			@Override public void sendChat(String message) { throw new AssertionError("unexpected chat"); }
			@Override public void sendCommand(String command) { sent.add(command); }
		};
		TeleportRequestButton button = new TeleportRequestButton(runtime,
				new ServerCommandService(runtime, new OutgoingChatService(transport, ignored -> { }, () -> true, runtime::generation)),
				() -> 1_000L);
		button.handleMessage("One просит телепортироваться к вам.");
		assertTrue(button.accept("One"));
		assertTrue(button.accept("One"));
		assertEquals(1, queued.size());
		assertTrue(button.visible());
		connected[0] = false;
		queued.removeFirst().run();
		assertTrue(button.visible());
		connected[0] = true;
		assertTrue(button.accept("One"));
		queued.removeFirst().run();
		assertFalse(button.visible());
		assertEquals(List.of("tpaccept One"), sent);
	}

	@Test
	void failedAutomaticAcceptFallsBackToManualButton() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		config.teleportAutoAcceptMode = TeleportAutoAcceptMode.EVERYONE;
		runtime.activate(config);
		ConnectedTransport transport = new ConnectedTransport();
		transport.connected = false;
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(transport, ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(runtime, commands, () -> 1_000L);

		button.handleMessage("Player просит телепортироваться к вам.");

		assertTrue(button.visible());
		assertTrue(transport.commands.isEmpty());
	}

	@Test
	void soundEventReferencesExistingVanillaAsset() throws IOException {
		try (var stream = getClass().getResourceAsStream("/assets/cndl_chat_plus/sounds.json")) {
			assertTrue(stream != null);
			assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8)
					.contains("minecraft:entity/shulker/ambient4"));
		}
	}

	private static final class ConnectedTransport implements OutgoingChatService.Transport {
		private final List<String> commands = new ArrayList<>();
		private boolean connected = true;
		@Override public boolean connected() { return connected; }
		@Override public void execute(Runnable action) { action.run(); }
		@Override public void sendChat(String message) { }
		@Override public void sendCommand(String command) { commands.add(command); }
	}
}
