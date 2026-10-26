package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class CustomChatOutgoingServiceTest {
	@Test
	void staleQueuedSendReportsFailureWithRecoverableTextAndNeverSendsChat() {
		List<Runnable> queued = new ArrayList<>();
		List<String> sent = new ArrayList<>();
		long[] generation = {1};
		OutgoingChatService.Transport transport = new OutgoingChatService.Transport() {
			@Override public boolean connected() { return true; }
			@Override public void execute(Runnable action) { queued.add(action); }
			@Override public void sendChat(String message) { throw new AssertionError("global fallback"); }
			@Override public void sendCommand(String command) { sent.add(command); }
		};
		ChatTabController tabs = controller(ResponderConfig.defaults());
		tabs.selectTab(custom(tabs), null);
		List<CustomChatOutgoingService.FailedMessage> failures = new ArrayList<>();
		CustomChatOutgoingService service = new CustomChatOutgoingService(tabs,
				new OutgoingChatService(transport, ignored -> { }, () -> true, () -> generation[0]), failures::add);
		assertTrue(service.route("черновик"));
		assertTrue(failures.isEmpty());
		generation[0]++;
		queued.removeFirst().run();
		assertTrue(sent.isEmpty());
		assertEquals(1, failures.size());
		assertEquals("черновик", failures.getFirst().text());
		assertEquals("gc черновик", failures.getFirst().command());
	}

	@Test
	void activeVoiceTabRoutesOrdinaryMessageAsGcCommandExactlyOnce() {
		RecordingTransport transport = new RecordingTransport();
		List<String> recorded = new ArrayList<>();
		ChatTabController tabs = controller(ResponderConfig.defaults());
		tabs.selectTab(custom(tabs), null);
		CustomChatOutgoingService service = new CustomChatOutgoingService(tabs,
				new OutgoingChatService(transport, recorded::add));

		assertTrue(service.route("  привет  "));
		assertEquals(List.of("/gc привет"), recorded);
		assertEquals(List.of("gc привет"), transport.commands);
	}

	@Test
	void slashInactiveCommandlessAndBlankMessagesAreNotIntercepted() {
		RecordingTransport transport = new RecordingTransport();
		ChatTabController tabs = controller(ResponderConfig.defaults());
		CustomChatOutgoingService service = new CustomChatOutgoingService(tabs,
				new OutgoingChatService(transport, ignored -> { }));

		assertFalse(service.route("plain"));
		tabs.selectTab(custom(tabs), null);
		assertFalse(service.route(" /spawn"));
		assertFalse(service.route("   "));

		ResponderConfig commandless = new ResponderConfig();
		commandless.customChatTabs = new ArrayList<>(List.of(
				new CustomChatTab("custom", "Custom", List.of(ChatTabSource.LOCAL), " ")));
		tabs.reloadConfig(commandless);
		tabs.selectTab(custom(tabs), null);
		assertFalse(service.route("plain"));
		assertTrue(transport.commands.isEmpty());
	}

	@Test
	void oversizedCommandBackedMessageIsInterceptedWithoutSending() {
		RecordingTransport transport = new RecordingTransport();
		List<String> recorded = new ArrayList<>();
		List<CustomChatOutgoingService.FailedMessage> failures = new ArrayList<>();
		ChatTabController tabs = controller(ResponderConfig.defaults());
		tabs.selectTab(custom(tabs), null);

		assertTrue(new CustomChatOutgoingService(tabs,
				new OutgoingChatService(transport, recorded::add), failures::add)
				.route("x".repeat(OutgoingChatService.MAX_OUTGOING_LENGTH)));
		assertTrue(recorded.isEmpty());
		assertTrue(transport.commands.isEmpty());
		assertEquals(1, failures.size());
		assertEquals("x".repeat(OutgoingChatService.MAX_OUTGOING_LENGTH), failures.getFirst().text());
	}

	@Test
	void disconnectedCommandBackedMessageIsInterceptedWithoutSending() {
		RecordingTransport transport = new RecordingTransport(false);
		List<String> recorded = new ArrayList<>();
		List<CustomChatOutgoingService.FailedMessage> failures = new ArrayList<>();
		ChatTabController tabs = controller(ResponderConfig.defaults());
		tabs.selectTab(custom(tabs), null);

		assertTrue(new CustomChatOutgoingService(tabs,
				new OutgoingChatService(transport, recorded::add), failures::add).route("привет"));
		assertTrue(recorded.isEmpty());
		assertTrue(transport.commands.isEmpty());
		assertEquals(1, failures.size());
		assertEquals("привет", failures.getFirst().text());
		assertEquals("gc привет", failures.getFirst().command());
	}

	@Test
	void disabledTabsDoNotRouteAnAlreadySelectedCustomTab() {
		RecordingTransport transport = new RecordingTransport();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(VanillaBoxConfig.fromCompatible(ResponderConfig.defaults()));
		ChatTabController tabs = new ChatTabController(new ChatTabClassifier(runtime), () -> false,
				ResponderConfig.defaults(), null);
		tabs.selectTab(custom(tabs), null);

		assertFalse(new CustomChatOutgoingService(tabs,
				new OutgoingChatService(transport, ignored -> { })).route("plain"));
		assertTrue(transport.commands.isEmpty());
	}

	private static ChatTabController controller(ResponderConfig config) {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(VanillaBoxConfig.fromCompatible(ResponderConfig.defaults()));
		return new ChatTabController(new ChatTabClassifier(runtime), () -> true, config, null);
	}

	private static ChatTabDefinition custom(ChatTabController tabs) {
		return tabs.definitions().stream().filter(ChatTabDefinition::custom).findFirst().orElseThrow();
	}

	private static final class RecordingTransport implements OutgoingChatService.Transport {
		private final List<String> commands = new ArrayList<>();
		private final boolean connected;

		private RecordingTransport() {
			this(true);
		}

		private RecordingTransport(boolean connected) {
			this.connected = connected;
		}

		@Override public boolean connected() { return connected; }
		@Override public void execute(Runnable action) { action.run(); }
		@Override public void sendChat(String message) { }
		@Override public void sendCommand(String command) { commands.add(command); }
	}
}
