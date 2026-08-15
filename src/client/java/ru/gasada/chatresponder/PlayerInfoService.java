package ru.gasada.chatresponder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;

public final class PlayerInfoService {
	private final ServerTemplateRuntime runtime;
	private final Function<String, CompletableFuture<VanillaGameProfileClient.FetchResult>> fetcher;
	private final FriendLookupManager lookupManager;
	private final MarriageLookupManager marriageLookupManager;
	private final Consumer<Runnable> clientExecutor;
	private final Map<String, PlayerInfoProfile> sessionCache = new LinkedHashMap<>();
	private volatile long epoch;
	private Object lastConnection;

	public PlayerInfoService(ServerTemplateRuntime runtime, VanillaGameProfileClient client,
			FriendLookupManager lookupManager) {
		this(runtime, client::fetch, lookupManager, null, Runnable::run);
	}

	PlayerInfoService(ServerTemplateRuntime runtime, VanillaGameProfileClient client,
			FriendLookupManager lookupManager, Consumer<Runnable> clientExecutor) {
		this(runtime, client::fetch, lookupManager, null, clientExecutor);
	}

	PlayerInfoService(ServerTemplateRuntime runtime, VanillaGameProfileClient client,
			FriendLookupManager lookupManager, MarriageLookupManager marriageLookupManager,
			Consumer<Runnable> clientExecutor) {
		this(runtime, client::fetch, lookupManager, marriageLookupManager, clientExecutor);
	}

	PlayerInfoService(ServerTemplateRuntime runtime,
			Function<String, CompletableFuture<VanillaGameProfileClient.FetchResult>> fetcher,
			FriendLookupManager lookupManager, Consumer<Runnable> clientExecutor) {
		this(runtime, fetcher, lookupManager, null, clientExecutor);
	}

	PlayerInfoService(ServerTemplateRuntime runtime,
			Function<String, CompletableFuture<VanillaGameProfileClient.FetchResult>> fetcher,
			FriendLookupManager lookupManager, MarriageLookupManager marriageLookupManager,
			Consumer<Runnable> clientExecutor) {
		this.runtime = runtime;
		this.fetcher = fetcher;
		this.lookupManager = lookupManager;
		this.marriageLookupManager = marriageLookupManager;
		this.clientExecutor = clientExecutor;
	}

	public Optional<PlayerInfoProfile> cached(String player) {
		return Optional.ofNullable(sessionCache.get(key(player)));
	}

	public CompletableFuture<LoadResult> refresh(String player) {
		PlayerNameValidator.ValidationResult validated = PlayerNameValidator.validate(player);
		if (!validated.valid()) return CompletableFuture.completedFuture(LoadResult.failure(validated.errorMessage()));
		ActiveTemplateSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (snapshot == null) return CompletableFuture.completedFuture(LoadResult.failure("Нет активного шаблона"));
		long generation = snapshot.generation();
		long requestEpoch = epoch;
		if (snapshot.playerInfo().provider() != PlayerInfoProvider.VANILLA_GAME_PUBLIC_API) {
			return lookupFallback(player, generation, requestEpoch, "Публичный профиль не настроен для шаблона");
		}
		return fetcher.apply(player).thenCompose(result -> {
			if (!sameRequest(generation, requestEpoch)) return CompletableFuture.completedFuture(LoadResult.stale());
			if (!result.success()) return lookupFallback(player, generation, requestEpoch, result.errorMessage());
			PlayerInfoProfile profile = result.profile();
			if (profile.username() == null) {
				return lookupFallback(player, generation, requestEpoch, "Ответ профиля не содержит ник игрока");
			}
			if (!profile.username().equalsIgnoreCase(player)) {
				return CompletableFuture.completedFuture(LoadResult.failure("Профиль относится к другому игроку"));
			}
			if (!profile.hasDetails()) {
				return lookupFallback(player, generation, requestEpoch, "API не вернул данных профиля");
			}
			return enrichMarriage(player, profile, generation, requestEpoch);
		});
	}

	public void resetRuntimeState() {
		epoch++;
		sessionCache.clear();
	}

	public void tick(Minecraft minecraft) {
		Object connection = minecraft == null ? null : minecraft.getConnection();
		if (connection != lastConnection) {
			resetRuntimeState();
			lastConnection = connection;
		}
	}

	private CompletableFuture<LoadResult> lookupFallback(String player, long generation, long requestEpoch,
			String apiError) {
		CompletableFuture<LoadResult> future = new CompletableFuture<>();
		if (lookupManager == null) {
			future.complete(LoadResult.failure(apiError + "; lookup недоступен"));
			return future;
		}
		clientExecutor.accept(() -> {
			if (!sameRequest(generation, requestEpoch)) {
				future.complete(LoadResult.stale());
				return;
			}
			if (!lookupManager.queueManualLookup(player, lookupData -> {
				if (!sameRequest(generation, requestEpoch)) future.complete(LoadResult.stale());
				else if (lookupData.hasData()) future.complete(LoadResult.fallback(lookupData));
				else future.complete(LoadResult.failure(apiError + "; lookup не вернул данные"));
			})) future.complete(LoadResult.failure(apiError + "; lookup недоступен"));
		});
		return future;
	}

	private CompletableFuture<LoadResult> enrichMarriage(String player, PlayerInfoProfile profile,
			long generation, long requestEpoch) {
		if (profile.marriage() != null || marriageLookupManager == null) {
			return publishProfile(player, profile, generation, requestEpoch, "Профиль обновлён");
		}
		CompletableFuture<LoadResult> future = new CompletableFuture<>();
		clientExecutor.accept(() -> {
			if (!sameRequest(generation, requestEpoch)) {
				future.complete(LoadResult.stale());
				return;
			}
			boolean queued = marriageLookupManager.queueLookup(player, result -> {
				if (!sameRequest(generation, requestEpoch)) {
					future.complete(LoadResult.stale());
					return;
				}
				PlayerInfoProfile enriched = result.partner() == null ? profile
						: withMarriage(profile, new PlayerInfoProfile.Marriage(result.partner(), null, null));
				String message = result.partner() != null ? "Профиль и супруг обновлены"
						: result.completed() ? "Профиль обновлён; брак не найден"
						: "Профиль обновлён; " + result.errorMessage();
				publishProfile(player, enriched, generation, requestEpoch, message)
						.whenComplete((value, error) -> {
							if (error != null) future.completeExceptionally(error);
							else future.complete(value);
						});
			});
			if (!queued) publishProfile(player, profile, generation, requestEpoch, "Профиль обновлён")
					.whenComplete((value, error) -> {
						if (error != null) future.completeExceptionally(error);
						else future.complete(value);
					});
		});
		return future;
	}

	private CompletableFuture<LoadResult> publishProfile(String player, PlayerInfoProfile profile,
			long generation, long requestEpoch, String message) {
		CompletableFuture<LoadResult> published = new CompletableFuture<>();
		clientExecutor.accept(() -> {
			if (!sameRequest(generation, requestEpoch)) published.complete(LoadResult.stale());
			else {
				sessionCache.put(key(player), profile);
				published.complete(LoadResult.success(profile, false, message));
			}
		});
		return published;
	}

	private static PlayerInfoProfile withMarriage(PlayerInfoProfile source,
			PlayerInfoProfile.Marriage marriage) {
		return new PlayerInfoProfile(source.username(), source.registrationDate(), source.lastLogin(),
				source.about(), source.city(), source.telegram(), source.vk(), source.website(), source.clan(),
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
			return success(profile, fallback, "");
		}
		static LoadResult success(PlayerInfoProfile profile, boolean fallback, String message) {
			return new LoadResult(true, profile, null, fallback, message);
		}
		static LoadResult fallback(PlayerLookupData lookupData) {
			String message = lookupData.lastSeen() == null ? "Данные получены через server lookup"
					: "Последнее посещение: " + lookupData.lastSeen();
			return new LoadResult(true, null, lookupData, true, message);
		}
		static LoadResult failure(String message) { return new LoadResult(false, null, null, false, message); }
		static LoadResult stale() { return failure("Устаревший ответ отброшен"); }
	}
}
