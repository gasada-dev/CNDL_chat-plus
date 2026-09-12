package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class VanillaBoxRuntimeTest {
	@Test
	void activatePublishesImmutableDeepSnapshotAndResetsOnce() {
		AtomicInteger resets = new AtomicInteger();
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		coordinator.register(resets::incrementAndGet);
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);

		VanillaBoxConfig first = config("Alice");
		first.teleportAutoAcceptMode = TeleportAutoAcceptMode.SELECTED_FRIENDS;
		first.teleportAutoAcceptFriends.add("Alice");
		first.mutedWords.add("*secret*");
		first.parsers.replyCandidateSeparators.clear();
		first.parsers.replyCandidateSeparators.add(" -> ");
		first.parsers.playerInfoPatterns.clear();
		first.parsers.playerInfoPatterns.put("city", "City: (.+)");
		VanillaBoxSnapshot snapshot = runtime.activate(first);

		first.teleportAutoAcceptFriends.add("MutatedAfterActivation");
		first.friends.add("MutatedAfterActivation");
		first.friendLastSeen.put("MutatedAfterActivation", "today");
		first.parsers.replyCandidateSeparators.add(" mutated ");
		first.parsers.playerInfoPatterns.put("mutated", "(.+)");

		assertEquals(1, resets.get());
		assertEquals("Alice", snapshot.friends().getFirst());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.friends().add("bad"));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.friendLastSeen().put("bad", "bad"));
		assertEquals(TeleportAutoAcceptMode.SELECTED_FRIENDS, snapshot.teleportAutoAcceptMode());
		assertEquals(List.of("Alice"), snapshot.teleportAutoAcceptFriends());
		assertThrows(UnsupportedOperationException.class,
				() -> snapshot.teleportAutoAcceptFriends().add("bad"));
		assertEquals(List.of(" -> "), snapshot.parsers().replyCandidateSeparators());
		assertEquals(Map.of("city", "City: (.+)"), snapshot.parsers().playerInfoPatterns());
		assertThrows(UnsupportedOperationException.class,
				() -> snapshot.parsers().replyCandidateSeparators().add("bad"));
		assertThrows(UnsupportedOperationException.class,
				() -> snapshot.parsers().playerInfoPatterns().put("bad", "bad"));
		assertTrue(snapshot.compiledParsers().playerInfoPatterns().containsKey("city"));
		assertEquals(1, snapshot.compiledFilters().size());
	}

	@Test
	void invalidParserRetainsExactSnapshotGenerationAndResetCount() {
		AtomicInteger resets = new AtomicInteger();
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		coordinator.register(resets::incrementAndGet);
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);
		VanillaBoxSnapshot first = runtime.activate(config("Alice"));
		VanillaBoxConfig invalid = config("Bob");
		invalid.parsers.lastSeenPattern = "[";

		assertThrows(IllegalArgumentException.class, () -> runtime.activate(invalid));

		assertEquals(1, resets.get());
		assertSame(first, runtime.activeSnapshot().orElseThrow());
		assertEquals(first.generation(), runtime.activeSnapshot().orElseThrow().generation());
		assertEquals(List.of("Alice"), runtime.activeSnapshot().orElseThrow().friends());
	}

	@Test
	void replacementAndClearEachResetOnceAndInvalidateGeneration() {
		AtomicInteger resets = new AtomicInteger();
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		coordinator.register(resets::incrementAndGet);
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);
		VanillaBoxSnapshot first = runtime.activate(config("Alice"));
		VanillaBoxSnapshot replacement = runtime.update(value -> value.friends = List.of("Bob")).orElseThrow();
		runtime.clear();

		assertEquals(3, resets.get());
		assertNotEquals(first.generation(), replacement.generation());
		assertTrue(runtime.activeSnapshot().isEmpty());
		assertTrue(runtime.generation() > replacement.generation());
	}

	@Test
	void lastSeenUpdatePublishesGenerationWithoutResettingQueues() {
		AtomicInteger resets = new AtomicInteger();
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		coordinator.register(resets::incrementAndGet);
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);
		VanillaBoxSnapshot first = runtime.activate(config("Alice"));

		VanillaBoxSnapshot updated = runtime.updateLastSeen("Alice", "yesterday").orElseThrow();

		assertEquals(1, resets.get());
		assertTrue(updated.generation() > first.generation());
		assertEquals(Map.of("Alice", "yesterday"), updated.friendLastSeen());
	}

	@Test
	void snapshotContainsNoSelectorIdentity() {
		assertFalse(java.util.Arrays.stream(VanillaBoxSnapshot.class.getRecordComponents())
				.map(java.lang.reflect.RecordComponent::getName)
				.anyMatch(name -> name.equals("id") || name.equals("name") || name.contains("selected")
						|| name.contains("default") || name.contains("binding")));
	}

	private static VanillaBoxConfig config(String friend) {
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.friends.add(friend);
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		config.parsers = ParserSettings.vanillaBoxDefaults();
		return config;
	}
}
