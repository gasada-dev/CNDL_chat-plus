package ru.gasada.cndlchatplus;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.BiFunction;

public final class PlayerInfoService {
	private final ServerTemplateRuntime runtime;
	private final BiFunction<String, String, CompletableFuture<VnbxPlayerRelationsResult>> bridgeFetcher;
	private final BooleanSupplier bridgeAvailable;
	private final FriendLookupManager lookupManager;
	private final Consumer<Runnable> clientExecutor;
	private final Map<String, PlayerInfoProfile> sessionCache = new HashMap<>();
	private final Map<String, CompletableFuture<LoadResult>> inFlight = new HashMap<>();
	private long epoch;

	PlayerInfoService(ServerTemplateRuntime runtime,
			BiFunction<String, String, CompletableFuture<VnbxPlayerRelationsResult>> bridgeFetcher,
			FriendLookupManager lookupManager, Consumer<Runnable> clientExecutor) {
		this(runtime, bridgeFetcher, () -> false, lookupManager, clientExecutor);
	}

	PlayerInfoService(ServerTemplateRuntime runtime,
			BiFunction<String, String, CompletableFuture<VnbxPlayerRelationsResult>> bridgeFetcher,
			BooleanSupplier bridgeAvailable, FriendLookupManager lookupManager, Consumer<Runnable> clientExecutor) {
		this.runtime = runtime;
		this.bridgeFetcher = bridgeFetcher;
		this.bridgeAvailable = bridgeAvailable;
		this.lookupManager = lookupManager;
		this.clientExecutor = clientExecutor;
	}

	public CompletableFuture<LoadResult> refresh(String player) {
		PlayerNameValidator.ValidationResult validated = PlayerNameValidator.validate(player);
		if (!validated.valid()) return CompletableFuture.completedFuture(LoadResult.failure(validated.errorMessage()));
		ActiveTemplateSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (snapshot == null) return CompletableFuture.completedFuture(LoadResult.failure("Нет активного шаблона"));
		long generation = snapshot.generation();
		long requestEpoch = epoch;
		boolean bridgePresent = bridgeAvailable.getAsBoolean();
		String requestKey = requestEpoch + ":" + generation + ":" + bridgePresent + ":" + key(player);
		CompletableFuture<LoadResult> existing = inFlight.get(requestKey);
		if (existing != null) return existing;
		CompletableFuture<LoadResult> shared = new CompletableFuture<>();
		inFlight.put(requestKey, shared);
		try {
			String requestId = VnbxBridgeClient.newRequestId();
			bridgeFetcher.apply(requestId, player).exceptionally(error -> {
				VnbxBridgeClient.logRequestFailed(requestId, player, "bridge_error");
				return VnbxPlayerRelationsResult.unavailable(requestId, player);
			})
					.thenCompose(bridge -> publishBridgeResult(player, bridge, generation, requestEpoch, bridgePresent))
					.whenComplete((result, error) -> {
						inFlight.remove(requestKey, shared);
						if (error == null) shared.complete(result);
						else shared.completeExceptionally(error);
					});
		} catch (RuntimeException error) {
			inFlight.remove(requestKey, shared);
			shared.completeExceptionally(error);
		}
		return shared;
	}

	private CompletableFuture<LoadResult> publishBridgeResult(String player, VnbxPlayerRelationsResult bridge,
			long generation, long requestEpoch, boolean bridgePresent) {
		CompletableFuture<LoadResult> future = new CompletableFuture<>();
		clientExecutor.accept(() -> {
			if (!sameRequest(generation, requestEpoch)) {
				future.complete(LoadResult.stale());
				return;
			}
			if (!bridge.hasRelations()) {
				if (bridgePresent) {
					VnbxBridgeClient.logFallback(bridge.requestId(), player, "skipped", "bridge_present");
					future.complete(LoadResult.failure("Данные игрока недоступны"));
					return;
				}
				lookupFallback(player, bridge.requestId(), generation, requestEpoch, "Данные игрока недоступны")
						.whenComplete((result, error) -> future.complete(error == null ? result
								: LoadResult.failure("Данные игрока недоступны")));
				return;
			}
			VnbxBridgeClient.logFallback(bridge.requestId(), player, "skipped", "bridge_success");
			PlayerInfoProfile profile = withBridgeRelations(emptyProfile(player), bridge);
			sessionCache.put(key(player), profile);
			future.complete(LoadResult.success(profile, false, new PlayerLookupData(null, bridge.fields())));
		});
		return future;
	}

	public void resetRuntimeState() {
		epoch++;
		sessionCache.clear();
		inFlight.clear();
	}

	public void tick(net.minecraft.client.Minecraft minecraft) {
		if (minecraft == null || minecraft.getConnection() == null) resetRuntimeState();
	}

	public Optional<PlayerInfoProfile> cached(String player) {
		return Optional.ofNullable(sessionCache.get(key(player)));
	}

	private CompletableFuture<LoadResult> lookupFallback(String player, String requestId, long generation, long requestEpoch,
			String message) {
		if (lookupManager == null) {
			VnbxBridgeClient.logFallback(requestId, player, "skipped", "fallback_unavailable");
			return CompletableFuture.completedFuture(LoadResult.failure(message));
		}
		CompletableFuture<LoadResult> future = new CompletableFuture<>();
		clientExecutor.accept(() -> {
			if (!sameRequest(generation, requestEpoch)) {
				VnbxBridgeClient.logFallback(requestId, player, "skipped", "stale");
				future.complete(LoadResult.stale());
				return;
			}
			if (!lookupManager.queueManualLookup(player, lookupData -> {
				if (!sameRequest(generation, requestEpoch)) future.complete(LoadResult.stale());
				else if (lookupData.hasData()) future.complete(LoadResult.lookup(lookupData));
				else future.complete(LoadResult.failure(message));
			})) {
				VnbxBridgeClient.logFallback(requestId, player, "skipped", "not_queued");
				future.complete(LoadResult.failure(message));
			} else VnbxBridgeClient.logFallback(requestId, player, "sent", "bridge_unavailable");
		});
		return future;
	}

	private static PlayerInfoProfile emptyProfile(String player) {
		return new PlayerInfoProfile(player, null, null, null, null, null, null, null, null, null, java.util.List.of());
	}

	private static PlayerInfoProfile withBridgeRelations(PlayerInfoProfile source,
			VnbxPlayerRelationsResult bridge) {
		PlayerInfoProfile.Clan clan = source.clan();
		PlayerInfoProfile.Marriage marriage = source.marriage();
		if (bridge.clanAvailable()) {
			VnbxPlayerRelationsResult.Clan value = bridge.clan();
			clan = new PlayerInfoProfile.Clan(value.tag(), value.name(), value.leader(), false, value.rank(),
					value.inClan());
		}
		if (bridge.marriageAvailable()) {
			VnbxPlayerRelationsResult.Marriage value = bridge.marriage();
			marriage = new PlayerInfoProfile.Marriage(value.partnerName(), value.date(), value.surname(),
					value.married());
		}
		return new PlayerInfoProfile(source.username(), source.registrationDate(), source.lastLogin(),
				source.about(), source.city(), source.telegram(), source.vk(), source.website(), clan,
				marriage, source.buildings());
	}

	private boolean sameRequest(long generation, long requestEpoch) {
		return epoch == requestEpoch
				&& runtime.activeSnapshot().map(value -> value.generation() == generation).orElse(false);
	}

	private static String key(String player) {
		return player == null ? "" : player.toLowerCase(Locale.ROOT);
	}

	public record LoadResult(boolean success, PlayerInfoProfile profile, PlayerLookupData lookupData,
			boolean fallback, String message) {
		static LoadResult success(PlayerInfoProfile profile, boolean fallback) {
			return success(profile, fallback, emptyLookup());
		}

		static LoadResult success(PlayerInfoProfile profile, boolean fallback, PlayerLookupData lookupData) {
			return new LoadResult(true, profile, lookupData, fallback, "Профиль обновлён");
		}

		static LoadResult lookup(PlayerLookupData lookupData) {
			return new LoadResult(true, null, lookupData, true, "Данные сервера обновлены");
		}

		static LoadResult failure(String message) {
			return new LoadResult(false, null, emptyLookup(), false, message);
		}

		static LoadResult stale() {
			return new LoadResult(false, null, emptyLookup(), false, "Устаревший запрос");
		}

		private static PlayerLookupData emptyLookup() {
			return new PlayerLookupData(null, Map.of());
		}
	}
}
