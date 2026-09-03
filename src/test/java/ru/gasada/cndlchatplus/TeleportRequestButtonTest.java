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
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-game", "Vanilla-game");
		template.commands.acceptTeleport = "tpaccept";
		ParserSettings.applyTeleportDefaults(template.parsers);
		runtime.switchTo(template);
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
	void ignoresMessagesWithoutActiveTemplateConfigurationAndResetsOnSwitch() {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("custom", "Custom");
		runtime.switchTo(template);
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(new ConnectedTransport(), ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(runtime, commands, () -> 1_000L);

		button.handleMessage("Player просит телепортироваться к вам.");
		assertFalse(button.visible());

		template.commands.acceptTeleport = "tpaccept";
		ParserSettings.applyTeleportDefaults(template.parsers);
		runtime.switchTo(template);
		button.handleMessage("Player просит телепортироваться к вам.");
		assertTrue(button.visible());
		runtime.clear();
		assertFalse(button.visible());
	}

	@Test
	void schedulesOneShulkerSoundPerRequest() {
		AtomicInteger sounds = new AtomicInteger();
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.switchTo(template);
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
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.switchTo(template);
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
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		template.friends.addAll(List.of("Alice", "Bob"));
		template.teleportAutoAcceptMode = TeleportAutoAcceptMode.SELECTED_FRIENDS;
		template.teleportAutoAcceptFriends.add("Alice");
		runtime.switchTo(template);
		ConnectedTransport transport = new ConnectedTransport();
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(transport, ignored -> { }));
		TeleportRequestButton button = new TeleportRequestButton(runtime, commands, () -> 1_000L);

		button.handleMessage("Bob просит телепортироваться к вам.");
		assertTrue(button.visible());
		assertTrue(transport.commands.isEmpty());

		button.handleMessage("alice просит телепортироваться к вам.");
		assertFalse(button.visible());
		assertEquals(List.of("tpaccept"), transport.commands);

		template.teleportAutoAcceptMode = TeleportAutoAcceptMode.FRIENDS;
		runtime.switchTo(template);
		button.handleMessage("Bob просит телепортироваться к вам.");
		assertFalse(button.visible());
		assertEquals(2, transport.commands.size());

		template.teleportAutoAcceptMode = TeleportAutoAcceptMode.EVERYONE;
		runtime.switchTo(template);
		button.handleMessage("Stranger просит телепортироваться к вам.");
		assertFalse(button.visible());
		assertEquals(3, transport.commands.size());
	}

	@Test
	void failedAutomaticAcceptFallsBackToManualButton() {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		template.teleportAutoAcceptMode = TeleportAutoAcceptMode.EVERYONE;
		runtime.switchTo(template);
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
