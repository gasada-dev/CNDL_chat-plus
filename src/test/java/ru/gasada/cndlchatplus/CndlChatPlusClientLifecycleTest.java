package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class CndlChatPlusClientLifecycleTest {
	@TempDir
	Path directory;

	@ParameterizedTest
	@MethodSource("allowedAddresses")
	void allowedJoinActivatesExactPersistedVanillaBox(String address, String normalized) throws Exception {
		Path main = initializedMain();
		VanillaBoxConfig persisted = VanillaBoxConfig.empty();
		persisted.friends = new ArrayList<>(List.of("PersistedOnly"));
		assertTrue(new VanillaBoxConfigStore(directory).save(persisted).success());

		ConfigManager.LoadedConfig loaded = ConfigManager.load(main);
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConnectionGate gate = new VanillaBoxConnectionGate();

		assertTrue(runtime.activeSnapshot().isEmpty());
		VanillaBoxConnectionGate.State state = CndlChatPlusClient.activateConnection(
				gate, runtime, loaded.vanillaBox(), address);

		assertTrue(state.active());
		assertEquals(normalized, state.normalizedAddress());
		assertEquals(List.of("PersistedOnly"), runtime.activeSnapshot().orElseThrow().friends());
		assertEquals(List.of("PersistedOnly"), loaded.config().friends);
	}

	@ParameterizedTest
	@MethodSource("deniedAddresses")
	void deniedLookalikeMalformedNullAndSingleplayerJoinClearRuntimeAndAddress(String address) {
		VanillaBoxRuntime runtime = activeRuntime();
		VanillaBoxConnectionGate gate = new VanillaBoxConnectionGate();
		gate.join("vanilla-box.ru");
		long generation = runtime.generation();

		VanillaBoxConnectionGate.State state = CndlChatPlusClient.activateConnection(
				gate, runtime, ConfigOperationResult.success(VanillaBoxConfig.empty()), address);

		assertFalse(state.active());
		assertEquals(null, state.normalizedAddress());
		assertTrue(runtime.activeSnapshot().isEmpty());
		assertEquals(generation + 1, runtime.generation());
	}

	@Test
	void missingOrCorruptInitializedPersistenceCannotActivateAllowedJoin() throws Exception {
		Path main = initializedMain();
		assertInactive(ConfigManager.load(main));

		Path vanilla = directory.resolve("server-templates/vanilla-box.json");
		Files.createDirectories(vanilla.getParent());
		Files.writeString(vanilla, "{");
		assertInactive(ConfigManager.load(main));
	}

	@Test
	void migrationFailureCannotActivateFileCreatedBeforeFailure() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		Files.writeString(main, "{\"storageVersion\":0}");
		Files.createDirectory(directory.resolve("server-templates.json"));

		ConfigManager.LoadedConfig loaded = ConfigManager.load(main);

		assertFalse(loaded.vanillaBox().success());
		assertTrue(Files.exists(directory.resolve("server-templates/vanilla-box.json")));
		assertInactive(loaded);
	}

	@Test
	void disconnectClearsGateAndInvalidatesRuntimeGenerationOnce() {
		VanillaBoxRuntime runtime = activeRuntime();
		VanillaBoxConnectionGate gate = new VanillaBoxConnectionGate();
		gate.join("vanilla-box.ru");
		long generation = runtime.generation();

		CndlChatPlusClient.disconnect(gate, runtime);

		assertFalse(gate.active());
		assertTrue(runtime.activeSnapshot().isEmpty());
		assertEquals(generation + 1, runtime.generation());
	}

	@Test
	void nearbyPlayerActionsRequireGateAndActiveRuntime() {
		VanillaBoxRuntime priorRuntime = CndlChatPlusClient.VANILLA_BOX_RUNTIME;
		try {
			CndlChatPlusClient.CONNECTION_GATE.disconnect();
			CndlChatPlusClient.VANILLA_BOX_RUNTIME = activeRuntime();
			assertFalse(NearbyPlayerMenuScreen.available());

			CndlChatPlusClient.CONNECTION_GATE.join("vanilla-box.ru");
			assertTrue(NearbyPlayerMenuScreen.available());

			CndlChatPlusClient.VANILLA_BOX_RUNTIME.clear();
			assertFalse(NearbyPlayerMenuScreen.available());
		} finally {
			CndlChatPlusClient.CONNECTION_GATE.disconnect();
			CndlChatPlusClient.VANILLA_BOX_RUNTIME = priorRuntime;
		}
	}

	private Path initializedMain() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		Files.writeString(main, "{\"storageVersion\":1}");
		return main;
	}

	private void assertInactive(ConfigManager.LoadedConfig loaded) {
		VanillaBoxRuntime runtime = activeRuntime();
		VanillaBoxConnectionGate.State state = CndlChatPlusClient.activateConnection(
				new VanillaBoxConnectionGate(), runtime, loaded.vanillaBox(), "vanilla-box.ru");
		assertFalse(loaded.vanillaBox().success());
		assertFalse(state.active());
		assertTrue(runtime.activeSnapshot().isEmpty());
	}

	private static VanillaBoxRuntime activeRuntime() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(VanillaBoxConfig.empty());
		return runtime;
	}

	private static Stream<Arguments> allowedAddresses() {
		return Stream.of(
				Arguments.of("vanilla-box.ru", "vanilla-box.ru:25565"),
				Arguments.of("MC.VANILLA-BOX.RU.:25566", "mc.vanilla-box.ru:25566"));
	}

	private static Stream<Arguments> deniedAddresses() {
		return Stream.of(
				Arguments.of((String) null),
				Arguments.of("vanilla-box.ru.evil"),
				Arguments.of("notvanilla-box.ru"),
				Arguments.of("vanilla-box.ru:0"),
				Arguments.of(" vanilla-box.ru"));
	}
}
