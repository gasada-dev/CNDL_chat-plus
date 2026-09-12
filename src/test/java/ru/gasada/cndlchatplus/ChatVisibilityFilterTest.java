package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChatVisibilityFilterTest {
	@Test
	void disabledDiscordIsHiddenBeforeMutedWordEvaluation() {
		VanillaBoxConfig config = vanillaBoxConfig();
		config.mutedWords.add("");
		VisibilityDecision decision = new ChatVisibilityFilter(runtimeFor(config), () -> false)
				.decide("[Discord] User » реклама");
		assertFalse(decision.visible());
		assertEquals(FilterReason.DISCORD_DISABLED, decision.reason());
	}

	@Test
	void discordToggleDoesNotDependOnVanillaBoxRuntime() {
		VanillaBoxConfig config = vanillaBoxConfig();
		config.discordChatEnabled = false;

		assertTrue(new ChatVisibilityFilter(runtimeFor(config), () -> true)
				.decide("[Discord] User » hello").visible());
	}

	@Test
	void discordMuteUsesVanillaBoxSnapshotAndIgnoresCase() {
		VanillaBoxConfig config = vanillaBoxConfig();
		config.discordMutedPlayers.add("User_Name");
		VisibilityDecision decision = filterFor(config).decide("[Discord] user_name » hello");
		assertEquals(FilterReason.DISCORD_USER_MUTED, decision.reason());
		assertEquals("user_name", decision.matchedValue());
	}

	@Test
	void compiledMutedFiltersPreserveFirstMatchAndWildcardSemantics() {
		VanillaBoxConfig config = vanillaBoxConfig();
		config.mutedWords.add("первый*");
		config.mutedWords.add("*второй*");
		VanillaBoxRuntime runtime = runtimeFor(config);
		VisibilityDecision decision = new ChatVisibilityFilter(runtime).decide("ПЕРВЫЙ и второй");
		assertEquals(FilterReason.MUTED_WORD, decision.reason());
		assertEquals("первый*", decision.matchedValue());
		assertEquals(2, runtime.activeSnapshot().orElseThrow().compiledFilters().size());
	}

	@Test
	void replacingConfigReplacesCompiledFiltersWithoutLeakage() {
		VanillaBoxConfig first = vanillaBoxConfig();
		first.mutedWords.add("alpha");
		VanillaBoxConfig second = vanillaBoxConfig();
		second.mutedWords.add("beta");
		VanillaBoxRuntime runtime = runtimeFor(first);
		ChatVisibilityFilter filter = new ChatVisibilityFilter(runtime);
		assertFalse(filter.decide("alpha").visible());
		runtime.activate(second);
		assertTrue(filter.decide("alpha").visible());
		assertFalse(filter.decide("beta").visible());
	}

	@Test
	void minecraftMuteRequiresExplicitSenderAndDoesNotParseOrdinaryText() {
		VanillaBoxConfig config = vanillaBoxConfig();
		config.mutedMinecraftPlayers.add("PlayerOne");
		ChatVisibilityFilter filter = filterFor(config);
		assertTrue(filter.decide("PlayerOne: hello").visible());
		assertEquals(FilterReason.MINECRAFT_PLAYER_MUTED,
				filter.decide("hello", "playerone").reason());
	}

	@Test
	void inactiveRuntimeFailsOpen() {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VisibilityDecision decision = new ChatVisibilityFilter(runtime).decide("hello");
		assertTrue(decision.visible());
		assertEquals(FilterReason.VISIBLE, decision.reason());
	}

	private static ChatVisibilityFilter filterFor(VanillaBoxConfig config) {
		return new ChatVisibilityFilter(runtimeFor(config));
	}

	private static VanillaBoxRuntime runtimeFor(VanillaBoxConfig config) {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(config);
		return runtime;
	}

	private static VanillaBoxConfig vanillaBoxConfig() {
		return VanillaBoxConfig.fromCompatible(new ResponderConfig());
	}
}
