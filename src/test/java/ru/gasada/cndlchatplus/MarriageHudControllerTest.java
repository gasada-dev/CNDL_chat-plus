package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class MarriageHudControllerTest {
	@Test
	void fetchesSelfOnlyOncePerConnectionAndGenerationAfterBridgeBecomesAvailable() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> bridge = new CompletableFuture<>();
		AtomicInteger fetches = new AtomicInteger();
		AtomicInteger usernameReads = new AtomicInteger();
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) -> {
			fetches.incrementAndGet();
			return bridge;
		}, () -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		Object connection = new Object();
		long generation = generation(runtime);

		controller.tick(connection, generation, false, () -> {
			usernameReads.incrementAndGet();
			return "Self_1";
		});
		controller.tick(connection, generation, true, () -> {
			usernameReads.incrementAndGet();
			return "Self_1";
		});
		controller.tick(connection, generation, true, () -> {
			usernameReads.incrementAndGet();
			return "Self_1";
		});

		assertEquals(1, usernameReads.get());
		assertEquals(1, fetches.get());
		bridge.complete(relations(true, "Partner_1"));
		controller.tick(connection, generation, true, () -> "Self_1");
		assertEquals(1, fetches.get());
		assertEquals("Partner_1", controller.snapshot().partner());
		controller.tick(connection, generation, true, () -> "Self_1");
		assertTrue(controller.snapshot().visible());
		assertEquals("Partner_1", controller.snapshot().partner());
		assertEquals(1, fetches.get());
	}

	@Test
	void bridgeLossHidesVisiblePartnerRejectsMenuContextAndAllowsOneRecoveryRefresh() {
		ServerTemplateRuntime runtime = runtime();
		AtomicInteger fetches = new AtomicInteger();
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) ->
				CompletableFuture.completedFuture(relations(true,
						fetches.getAndIncrement() == 0 ? "Partner_1" : "Partner_2")),
				() -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		Object connection = new Object();
		long generation = generation(runtime);
		controller.tick(connection, generation, true, () -> "Self_1");
		MarriageHudSnapshot.Context staleContext = controller.snapshot().context();
		RecordingTransport transport = new RecordingTransport();
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(transport, ignored -> { }));

		controller.tick(connection, generation, false, () -> "Self_1");

		assertFalse(controller.snapshot().visible());
		assertFalse(controller.execute(staleContext, MarriageAction.KISS, commands).success());
		assertTrue(transport.commands.isEmpty());

		controller.tick(connection, generation, false, () -> "Self_1");
		controller.tick(connection, generation, true, () -> "Self_1");
		controller.tick(connection, generation, true, () -> "Self_1");

		assertEquals(2, fetches.get());
		assertEquals("Partner_2", controller.snapshot().partner());
	}

	@Test
	void inFlightCompletionAfterBridgeLossCannotRepublishAndFalseTicksDoNotRequest() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> pending = new CompletableFuture<>();
		AtomicInteger fetches = new AtomicInteger();
		AtomicInteger usernameReads = new AtomicInteger();
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) -> {
			fetches.incrementAndGet();
			return pending;
		}, () -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		Object connection = new Object();
		long generation = generation(runtime);
		java.util.function.Supplier<String> username = () -> {
			usernameReads.incrementAndGet();
			return "Self_1";
		};

		controller.tick(connection, generation, true, username);
		controller.tick(connection, generation, false, username);
		controller.tick(connection, generation, false, username);
		pending.complete(relations(true, "Stale_1"));

		assertFalse(controller.snapshot().visible());
		assertEquals(1, fetches.get());
		assertEquals(1, usernameReads.get());
	}

	@Test
	void recoveryCanAttachOneCurrentRefreshToSameModeInFlightRequest() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> pending = new CompletableFuture<>();
		AtomicInteger fetches = new AtomicInteger();
		AtomicInteger usernameReads = new AtomicInteger();
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) -> {
			fetches.incrementAndGet();
			return pending;
		}, () -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		Object connection = new Object();
		long generation = generation(runtime);
		java.util.function.Supplier<String> username = () -> {
			usernameReads.incrementAndGet();
			return "Self_1";
		};

		controller.tick(connection, generation, true, username);
		controller.tick(connection, generation, false, username);
		controller.tick(connection, generation, true, username);
		controller.tick(connection, generation, true, username);
		pending.complete(relations(true, "Partner_1"));

		assertEquals(1, fetches.get());
		assertEquals(2, usernameReads.get());
		assertEquals("Partner_1", controller.snapshot().partner());
	}

	@Test
	void rejectsStaleCompletionAndFetchesAgainForNewContext() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> stale = new CompletableFuture<>();
		CompletableFuture<VnbxPlayerRelationsResult> current = new CompletableFuture<>();
		AtomicInteger fetches = new AtomicInteger();
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) ->
				fetches.getAndIncrement() == 0 ? stale : current, () -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		Object firstConnection = new Object();
		Object secondConnection = new Object();
		long generation = generation(runtime);

		controller.tick(firstConnection, generation, true, () -> "Self_1");
		controller.resetRuntimeState();
		playerInfo.resetRuntimeState();
		controller.tick(secondConnection, generation, true, () -> "Self_1");
		stale.complete(relations(true, "Stale_1"));

		assertFalse(controller.snapshot().visible());
		current.complete(relations(true, "Current_1"));
		assertEquals("Current_1", controller.snapshot().partner());
		assertEquals(2, fetches.get());
	}

	@Test
	void resetAndNewGenerationAllowOneFreshFetch() {
		ServerTemplateRuntime runtime = runtime();
		AtomicInteger fetches = new AtomicInteger();
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) -> {
			fetches.incrementAndGet();
			return CompletableFuture.completedFuture(relations(true, "Partner_1"));
		}, () -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		Object connection = new Object();

		controller.tick(connection, generation(runtime), true, () -> "Self_1");
		controller.resetRuntimeState();
		playerInfo.resetRuntimeState();
		controller.tick(connection, generation(runtime), true, () -> "Self_1");
		runtime.switchTo(ServerTemplate.empty("second", "Second"));
		controller.tick(connection, generation(runtime), true, () -> "Self_1");

		assertEquals(3, fetches.get());
	}

	@Test
	void unavailableUnmarriedAndInvalidPartnersStayHidden() {
		ServerTemplateRuntime runtime = runtime();
		AtomicInteger fetches = new AtomicInteger();
		List<VnbxPlayerRelationsResult> responses = List.of(
				relations(false, null), relations(true, "  "), relations(true, "bad partner"));
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) ->
				CompletableFuture.completedFuture(responses.get(fetches.getAndIncrement())),
				() -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		AtomicInteger usernameReads = new AtomicInteger();

		controller.tick(new Object(), generation(runtime), false, () -> {
			usernameReads.incrementAndGet();
			return "Self_1";
		});
		assertFalse(controller.snapshot().visible());
		assertEquals(0, usernameReads.get());
		for (int index = 0; index < responses.size(); index++) {
			controller.tick(new Object(), generation(runtime), true, () -> "Self_1");
			assertFalse(controller.snapshot().visible());
		}
	}

	@Test
	void dispatchesExactActionsAndFailsClosedForBlankOrStaleContext() {
		ServerTemplateRuntime runtime = runtime();
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) ->
				CompletableFuture.completedFuture(relations(true, "Partner_1")),
				() -> true, null, Runnable::run);
		MarriageHudController controller = new MarriageHudController(playerInfo);
		Object connection = new Object();
		long generation = generation(runtime);
		controller.tick(connection, generation, true, () -> "Self_1");
		MarriageHudSnapshot.Context context = controller.snapshot().context();
		RecordingTransport transport = new RecordingTransport();
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(transport, ignored -> { }));

		assertTrue(controller.execute(context, MarriageAction.KISS, commands).success());
		assertTrue(controller.execute(context, MarriageAction.HOME, commands).success());
		assertTrue(controller.execute(context, MarriageAction.TP, commands).success());
		assertEquals(List.of("marry kiss", "marry home", "marry tp"), transport.commands);

		ServerTemplate blank = ServerTemplate.empty("blank", "Blank");
		runtime.switchTo(blank);
		controller.tick(connection, generation(runtime), true, () -> "Self_1");
		MarriageHudSnapshot.Context blankContext = controller.snapshot().context();
		assertFalse(controller.execute(blankContext, MarriageAction.KISS, commands).success());
		assertEquals(3, transport.commands.size());

		controller.resetRuntimeState();
		assertFalse(controller.execute(blankContext, MarriageAction.HOME, commands).success());
		assertEquals(3, transport.commands.size());
	}

	@Test
	void exposesExactlyThreeRussianActionsInMenuOrder() {
		assertEquals(List.of("Поцеловать", "Тп домой", "Тп к любви"),
				java.util.Arrays.stream(MarriageAction.values()).map(MarriageAction::label).toList());
	}

	private static ServerTemplateRuntime runtime() {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		runtime.switchTo(template);
		return runtime;
	}

	private static long generation(ServerTemplateRuntime runtime) {
		return runtime.activeSnapshot().orElseThrow().generation();
	}

	private static VnbxPlayerRelationsResult relations(boolean married, String partner) {
		return new VnbxPlayerRelationsResult(true, "Self_1", false,
				new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
				new VnbxPlayerRelationsResult.Marriage(married, null, partner));
	}

	private static final class RecordingTransport implements OutgoingChatService.Transport {
		private final List<String> commands = new ArrayList<>();
		@Override public boolean connected() { return true; }
		@Override public void execute(Runnable action) { action.run(); }
		@Override public void sendChat(String message) { }
		@Override public void sendCommand(String command) { commands.add(command); }
	}
}
