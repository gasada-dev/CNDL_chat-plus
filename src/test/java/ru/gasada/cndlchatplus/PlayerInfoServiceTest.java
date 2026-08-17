package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

final class PlayerInfoServiceTest {
	@Test
	void cachesSuccessfulProfileOnlyInsideCurrentTemplateGeneration() {
		ServerTemplateRuntime runtime = runtimeWithApi("first");
		PlayerInfoProfile profile = new PlayerInfoProfile("Player_1", null, null, null, "Москва",
				null, null, null, null, null, List.of());
		PlayerInfoService service = new PlayerInfoService(runtime,
				ignored -> CompletableFuture.completedFuture(
						VanillaGameProfileClient.FetchResult.success(profile)), null, Runnable::run);

		assertTrue(service.refresh("Player_1").join().success());
		assertEquals(profile, service.cached("player_1").orElseThrow());
		service.resetRuntimeState();
		assertTrue(service.cached("Player_1").isEmpty());
	}

	@Test
	void profileWithOnlyNicknameUsesManualLookupFallback() {
		ServerTemplateRuntime runtime = runtimeWithApi("first");
		PlayerInfoService service = new PlayerInfoService(runtime,
				ignored -> CompletableFuture.completedFuture(VanillaGameProfileClient.FetchResult.success(
						new PlayerInfoProfile("Player_1", null, null, null, null, null, null, null,
								null, null, List.of()))), null, Runnable::run);

		assertFalse(service.refresh("Player_1").join().success());
	}

	@Test
	void rejectsResponseAfterTemplateSwitchAndWrongPlayerResponse() {
		ServerTemplateRuntime runtime = runtimeWithApi("first");
		CompletableFuture<VanillaGameProfileClient.FetchResult> pending = new CompletableFuture<>();
		PlayerInfoService service = new PlayerInfoService(runtime, ignored -> pending, null, Runnable::run);
		CompletableFuture<PlayerInfoService.LoadResult> load = service.refresh("Player_1");
		runtime.switchTo(ServerTemplate.empty("second", "Second"));
		pending.complete(VanillaGameProfileClient.FetchResult.success(new PlayerInfoProfile(
				"Player_1", null, null, null, null, null, null, null, null, null, List.of())));
		assertFalse(load.join().success());
		assertTrue(service.cached("Player_1").isEmpty());

		ServerTemplate api = ServerTemplate.empty("third", "Third");
		api.playerInfo.provider = PlayerInfoProvider.VANILLA_GAME_PUBLIC_API;
		runtime.switchTo(api);
		PlayerInfoService wrong = new PlayerInfoService(runtime, ignored -> CompletableFuture.completedFuture(
				VanillaGameProfileClient.FetchResult.success(new PlayerInfoProfile(
						"Other", null, null, null, null, null, null, null, null, null, List.of()))),
				null, Runnable::run);
		assertFalse(wrong.refresh("Player_1").join().success());

		PlayerInfoService missing = new PlayerInfoService(runtime, ignored -> CompletableFuture.completedFuture(
				VanillaGameProfileClient.FetchResult.success(new PlayerInfoProfile(
						null, null, null, null, null, null, null, null, null, null, List.of()))),
				null, Runnable::run);
		assertFalse(missing.refresh("Player_1").join().success());
	}

	@Test
	void enrichesApiProfileWithMarriageListWhenApiMarriageIsNull() {
		ServerTemplateRuntime runtime = runtimeWithApi("vanilla-game");
		ServerTemplate configured = ServerTemplate.empty("vanilla-game", "Vanilla-game");
		configured.playerInfo.provider = PlayerInfoProvider.VANILLA_GAME_PUBLIC_API;
		configured.commands.marriageList = "marry list {page}";
		ParserSettings.applyVanillaGameMarriageDefaults(configured.parsers);
		runtime.switchTo(configured);
		MarriageLookupManager marriages = new MarriageLookupManager(runtime, ignored -> true,
				new ServerLookupCoordinator(), System::currentTimeMillis);
		PlayerInfoProfile profile = new PlayerInfoProfile("Player_1", "01.01.2026", null, null,
				"Москва", null, null, null, null, null, List.of());
		PlayerInfoService service = new PlayerInfoService(runtime,
				ignored -> CompletableFuture.completedFuture(
						VanillaGameProfileClient.FetchResult.success(profile)), null, marriages, Runnable::run);

		CompletableFuture<PlayerInfoService.LoadResult> load = service.refresh("Player_1");
		marriages.tick(true);
		marriages.handleMessage("Список замужних игроков - страница 1/1");
		marriages.handleMessage("Player_1 ❤ Partner_2");

		PlayerInfoService.LoadResult result = load.join();
		assertTrue(result.success());
		assertEquals("Partner_2", result.profile().marriage().partner());
		assertEquals("Москва", result.profile().city());
		assertEquals(result.profile(), service.cached("player_1").orElseThrow());
	}

	private static ServerTemplateRuntime runtimeWithApi(String id) {
		ServerTemplate template = ServerTemplate.empty(id, id);
		template.playerInfo.provider = PlayerInfoProvider.VANILLA_GAME_PUBLIC_API;
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(template);
		return runtime;
	}
}
