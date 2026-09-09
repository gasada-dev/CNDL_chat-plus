package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

final class PlayerInfoServiceTest {
	@Test
	void coalescesConcurrentRefreshesForSamePlayerIgnoringCase() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> pending = new CompletableFuture<>();
		AtomicInteger fetches = new AtomicInteger();
		PlayerInfoService service = new PlayerInfoService(runtime, (requestId, ignored) -> {
			fetches.incrementAndGet();
			return pending;
		}, null, Runnable::run);

		CompletableFuture<PlayerInfoService.LoadResult> first = service.refresh("Player_1");
		CompletableFuture<PlayerInfoService.LoadResult> second = service.refresh("player_1");

		assertEquals(1, fetches.get());
		pending.complete(new VnbxPlayerRelationsResult(true, "Player_1", false,
				new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
				new VnbxPlayerRelationsResult.Marriage(false, null, null)));
		assertTrue(first.join().success());
		assertTrue(second.join().success());
		assertTrue(service.refresh("PLAYER_1").join().success());
		assertEquals(2, fetches.get());
	}

	@Test
	void availableBridgeRefreshDoesNotCoalesceWithPendingUnavailableFallback() {
		ServerTemplateRuntime runtime = runtime();
		FriendLookupManager lookupManager = new FriendLookupManager(new ResponderConfig());
		AtomicBoolean bridgeAvailable = new AtomicBoolean();
		AtomicInteger fetches = new AtomicInteger();
		PlayerInfoService service = new PlayerInfoService(runtime, (requestId, player) -> {
			fetches.incrementAndGet();
			return CompletableFuture.completedFuture(bridgeAvailable.get()
					? new VnbxPlayerRelationsResult(true, player, false,
							new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
							new VnbxPlayerRelationsResult.Marriage(true, null, "Partner_1"))
					: VnbxPlayerRelationsResult.unavailable(requestId, player));
		}, bridgeAvailable::get, lookupManager, Runnable::run);

		CompletableFuture<PlayerInfoService.LoadResult> fallback = service.refresh("Player_1");
		assertFalse(fallback.isDone());
		assertEquals(1, lookupManager.queuedCount());

		bridgeAvailable.set(true);
		CompletableFuture<PlayerInfoService.LoadResult> bridgeRefresh = service.refresh("player_1");
		assertEquals(2, fetches.get());
		PlayerInfoService.LoadResult bridge = bridgeRefresh.join();
		assertTrue(bridge.success());
		assertFalse(bridge.fallback());
		assertEquals("Partner_1", bridge.profile().marriage().partner());
		assertEquals("Partner_1", service.cached("PLAYER_1").orElseThrow().marriage().partner());
		lookupManager.resetRuntimeState();
	}

	@Test
	void resetDoesNotLetStaleInFlightRequestBlockOrPopulateNextContext() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> staleBridge = new CompletableFuture<>();
		CompletableFuture<VnbxPlayerRelationsResult> currentBridge = new CompletableFuture<>();
		AtomicInteger fetches = new AtomicInteger();
		PlayerInfoService service = new PlayerInfoService(runtime, (requestId, ignored) ->
				fetches.getAndIncrement() == 0 ? staleBridge : currentBridge, null, Runnable::run);
		CompletableFuture<PlayerInfoService.LoadResult> stale = service.refresh("Player_1");

		service.resetRuntimeState();
		CompletableFuture<PlayerInfoService.LoadResult> current = service.refresh("player_1");
		assertEquals(2, fetches.get());

		VnbxPlayerRelationsResult bridge = new VnbxPlayerRelationsResult(true, "Player_1", false,
				new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
				new VnbxPlayerRelationsResult.Marriage(false, null, null));
		staleBridge.complete(bridge);
		assertFalse(stale.join().success());
		assertTrue(service.cached("Player_1").isEmpty());
		currentBridge.complete(bridge);
		assertTrue(current.join().success());
		assertTrue(service.cached("Player_1").isPresent());
	}

	@Test
	void cachesBridgeProfileOnlyInsideCurrentTemplateGeneration() {
		ServerTemplateRuntime runtime = runtime();
		VnbxPlayerRelationsResult bridge = new VnbxPlayerRelationsResult(true, "Player_1", true,
				new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
				new VnbxPlayerRelationsResult.Marriage(true, "uuid", "Partner"));
		PlayerInfoService service = service(runtime, bridge);

		PlayerInfoService.LoadResult result = service.refresh("Player_1").join();
		assertTrue(result.success());
		assertFalse(result.profile().clan().inClan());
		assertEquals("Partner", result.profile().marriage().partner());
		assertEquals(result.profile(), service.cached("player_1").orElseThrow());
		service.resetRuntimeState();
		assertTrue(service.cached("Player_1").isEmpty());
	}

	@Test
	void rejectsResponseAfterTemplateSwitch() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> staleBridge = new CompletableFuture<>();
		CompletableFuture<VnbxPlayerRelationsResult> currentBridge = new CompletableFuture<>();
		AtomicInteger fetches = new AtomicInteger();
		PlayerInfoService service = new PlayerInfoService(runtime, (requestId, ignored) ->
				fetches.getAndIncrement() == 0 ? staleBridge : currentBridge, null, Runnable::run);
		CompletableFuture<PlayerInfoService.LoadResult> stale = service.refresh("Player_1");
		runtime.switchTo(ServerTemplate.empty("second", "Second"));
		CompletableFuture<PlayerInfoService.LoadResult> current = service.refresh("player_1");
		assertEquals(2, fetches.get());

		VnbxPlayerRelationsResult bridge = new VnbxPlayerRelationsResult(true, "Player_1", false,
				new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
				new VnbxPlayerRelationsResult.Marriage(false, null, null));
		staleBridge.complete(bridge);

		assertFalse(stale.join().success());
		assertTrue(service.cached("Player_1").isEmpty());
		currentBridge.complete(bridge);
		assertTrue(current.join().success());
	}

	@Test
	void treatsAvailableNegativeMarriageAsAuthoritativeWithoutClanFallback() throws Exception {
		ServerTemplateRuntime runtime = runtime();
		VnbxPlayerRelationsResult bridge = new VnbxPlayerRelationsResult(true, "Player_1", false,
				new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
				new VnbxPlayerRelationsResult.Marriage(false, null, null));

		PlayerInfoService service = new PlayerInfoService(runtime, (requestId, ignored) -> CompletableFuture.completedFuture(bridge),
				new FriendLookupManager(new ResponderConfig()), Runnable::run);
		PlayerInfoService.LoadResult result = service.refresh("Player_1").get(1, TimeUnit.SECONDS);

		assertTrue(result.success());
		assertFalse(result.fallback());
		assertFalse(result.profile().marriage().married());
	}

	@Test
	void publishesBridgeProfileOnClientExecutor() {
		ServerTemplateRuntime runtime = runtime();
		CompletableFuture<VnbxPlayerRelationsResult> bridgeFuture = new CompletableFuture<>();
		Deque<Runnable> clientTasks = new ArrayDeque<>();
		PlayerInfoService service = new PlayerInfoService(runtime, (requestId, ignored) -> bridgeFuture, null, clientTasks::addLast);
		VnbxPlayerRelationsResult bridge = new VnbxPlayerRelationsResult(true, "Player_1", true,
				new VnbxPlayerRelationsResult.Clan(false, null, null, null), false,
				new VnbxPlayerRelationsResult.Marriage(false, null, null));

		CompletableFuture<PlayerInfoService.LoadResult> result = service.refresh("Player_1");
		CompletableFuture.runAsync(() -> bridgeFuture.complete(bridge)).join();

		assertTrue(service.cached("Player_1").isEmpty());
		assertFalse(result.isDone());
		clientTasks.removeFirst().run();
		assertEquals(result.join().profile(), service.cached("Player_1").orElseThrow());
	}

	@Test
	void queuesManualLookupWhenBridgeIsUnavailable() {
		ServerTemplateRuntime runtime = runtime();
		FriendLookupManager lookupManager = new FriendLookupManager(new ResponderConfig());
		PlayerInfoService service = new PlayerInfoService(runtime,
				(requestId, ignored) -> CompletableFuture.completedFuture(VnbxPlayerRelationsResult.unavailable(requestId, ignored)), lookupManager,
				Runnable::run);

		CompletableFuture<PlayerInfoService.LoadResult> result = service.refresh("Player_1");

		assertFalse(result.isDone());
		assertEquals(1, lookupManager.queuedCount());
		lookupManager.resetRuntimeState();
	}

	@Test
	void retainsCallerRequestIdWhenBridgeFutureFails() {
		ServerTemplateRuntime runtime = runtime();
		FriendLookupManager lookupManager = new FriendLookupManager(new ResponderConfig());
		AtomicReference<String> requestId = new AtomicReference<>();
		AtomicReference<String> target = new AtomicReference<>();
		PlayerInfoService service = new PlayerInfoService(runtime, (id, player) -> {
			requestId.set(id);
			target.set(player);
			return CompletableFuture.failedFuture(new IllegalStateException());
		}, lookupManager, Runnable::run);

		CompletableFuture<PlayerInfoService.LoadResult> result = service.refresh("Player_1");

		assertTrue(requestId.get().matches("[A-Za-z0-9_-]{1,64}"));
		assertEquals("Player_1", target.get());
		assertFalse(result.isDone());
		assertEquals(1, lookupManager.queuedCount());
		lookupManager.resetRuntimeState();
	}

	@Test
	void publishesExtendedBridgeFieldsWithoutLookup() {
		ServerTemplateRuntime runtime = runtime();
		FriendLookupManager lookupManager = new FriendLookupManager(new ResponderConfig());
		VnbxPlayerRelationsResult bridge = new VnbxPlayerRelationsResult(true, "id-1", "Player_1", true,
				new VnbxPlayerRelationsResult.Clan(true, "TAG", "Clan", "Офицер", "Leader_1"), true,
				new VnbxPlayerRelationsResult.Marriage(true, "uuid", "Partner", "01.02.2026", "Иванов"),
				java.util.Map.of("Статус", "Активен", "КПД / KDR", "1.5"));
		PlayerInfoService service = new PlayerInfoService(runtime,
				(requestId, ignored) -> CompletableFuture.completedFuture(bridge), () -> true, lookupManager,
				Runnable::run);

		PlayerInfoService.LoadResult result = service.refresh("Player_1").join();

		assertTrue(result.success());
		assertFalse(result.fallback());
		assertEquals("Leader_1", result.profile().clan().leaderName());
		assertEquals("01.02.2026", result.profile().marriage().date());
		assertEquals("Иванов", result.profile().marriage().surname());
		assertEquals("1.5", result.lookupData().fields().get("КПД / KDR"));
		assertEquals(0, lookupManager.queuedCount());
	}

	@Test
	void skipsChatLookupFallbackWhenBridgeIsPresent() {
		ServerTemplateRuntime runtime = runtime();
		FriendLookupManager lookupManager = new FriendLookupManager(new ResponderConfig());
		PlayerInfoService service = new PlayerInfoService(runtime,
				(requestId, player) -> CompletableFuture.completedFuture(
						VnbxPlayerRelationsResult.unavailable(requestId, player)),
				() -> true, lookupManager, Runnable::run);

		PlayerInfoService.LoadResult result = service.refresh("Player_1").join();

		assertFalse(result.success());
		assertEquals("Данные игрока недоступны", result.message());
		assertEquals(0, lookupManager.queuedCount());
	}

	private static PlayerInfoService service(ServerTemplateRuntime runtime, VnbxPlayerRelationsResult bridge) {
		return new PlayerInfoService(runtime, (requestId, ignored) -> CompletableFuture.completedFuture(bridge), null, Runnable::run);
	}

	private static ServerTemplateRuntime runtime() {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(ServerTemplate.empty("vanilla-box", "Vanilla-box"));
		return runtime;
	}
}
