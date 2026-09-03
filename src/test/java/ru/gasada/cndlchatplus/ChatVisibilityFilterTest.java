package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChatVisibilityFilterTest {
	@Test
	void disabledDiscordIsHiddenBeforeMutedWordEvaluation() {
		ServerTemplate template = vanillaTemplate();
		template.mutedWords.add("");
		VisibilityDecision decision = new ChatVisibilityFilter(runtimeFor(template), () -> false)
				.decide("[Discord] User » реклама");
		assertFalse(decision.visible());
		assertEquals(FilterReason.DISCORD_DISABLED, decision.reason());
	}

	@Test
	void discordToggleDoesNotDependOnActiveTemplate() {
		ServerTemplate template = vanillaTemplate();
		template.discordChatEnabled = false;

		assertTrue(new ChatVisibilityFilter(runtimeFor(template), () -> true)
				.decide("[Discord] User » hello").visible());
	}

	@Test
	void discordMuteUsesActiveTemplateAndIgnoresCase() {
		ServerTemplate template = vanillaTemplate();
		template.discordMutedPlayers.add("User_Name");
		VisibilityDecision decision = filterFor(template).decide("[Discord] user_name » hello");
		assertEquals(FilterReason.DISCORD_USER_MUTED, decision.reason());
		assertEquals("user_name", decision.matchedValue());
	}

	@Test
	void compiledMutedFiltersPreserveFirstMatchAndWildcardSemantics() {
		ServerTemplate template = vanillaTemplate();
		template.mutedWords.add("первый*");
		template.mutedWords.add("*второй*");
		ServerTemplateRuntime runtime = runtimeFor(template);
		VisibilityDecision decision = new ChatVisibilityFilter(runtime).decide("ПЕРВЫЙ и второй");
		assertEquals(FilterReason.MUTED_WORD, decision.reason());
		assertEquals("первый*", decision.matchedValue());
		assertEquals(2, runtime.compiledFilters().orElseThrow().size());
	}

	@Test
	void switchingTemplateReplacesCompiledFiltersWithoutLeakage() {
		ServerTemplate first = vanillaTemplate();
		first.mutedWords.add("alpha");
		ServerTemplate second = vanillaTemplate();
		second.id = "second";
		second.name = "Second";
		second.mutedWords.add("beta");
		ServerTemplateRuntime runtime = runtimeFor(first);
		ChatVisibilityFilter filter = new ChatVisibilityFilter(runtime);
		assertFalse(filter.decide("alpha").visible());
		runtime.switchTo(second);
		assertTrue(filter.decide("alpha").visible());
		assertFalse(filter.decide("beta").visible());
	}

	@Test
	void minecraftMuteRequiresExplicitSenderAndDoesNotParseOrdinaryText() {
		ServerTemplate template = vanillaTemplate();
		template.mutedMinecraftPlayers.add("PlayerOne");
		ChatVisibilityFilter filter = filterFor(template);
		assertTrue(filter.decide("PlayerOne: hello").visible());
		assertEquals(FilterReason.MINECRAFT_PLAYER_MUTED,
				filter.decide("hello", "playerone").reason());
	}

	@Test
	void absenceOfActiveTemplateFailsOpen() {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		VisibilityDecision decision = new ChatVisibilityFilter(runtime).decide("hello");
		assertTrue(decision.visible());
		assertEquals(FilterReason.VISIBLE, decision.reason());
	}

	private static ChatVisibilityFilter filterFor(ServerTemplate template) {
		return new ChatVisibilityFilter(runtimeFor(template));
	}

	private static ServerTemplateRuntime runtimeFor(ServerTemplate template) {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(template);
		return runtime;
	}

	private static ServerTemplate vanillaTemplate() {
		return LegacyConfigToVanillaBoxMigration.fromLegacy(new ResponderConfig());
	}
}
