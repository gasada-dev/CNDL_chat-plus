package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

final class ResponderUiComponentsTest {
	@Test
	void paginationHandlesEmptyBoundaryAndLastPartialPage() {
		assertEquals(0, Pagination.maxPage(0, 5));
		assertEquals(0, Pagination.maxPage(5, 5));
		assertEquals(1, Pagination.maxPage(6, 5));
		assertEquals(2, Pagination.clampPage(99, 11, 5));
		assertEquals(0, Pagination.clampPage(-1, 11, 5));
	}

	@Test
	void screenStatusOwnsTextAndColorLifecycle() {
		ScreenStatus status = new ScreenStatus();
		assertTrue(status.empty());
		status.set("Ошибка", 0x12345678);
		assertFalse(status.empty());
		assertEquals("Ошибка", status.text());
		assertEquals(0x12345678, status.color());
		status.clear();
		assertTrue(status.empty());
	}

	@Test
	void rulesControllerOwnsRuleMutations() {
		ResponderConfig config = new ResponderConfig();
		RulesTabController controller = new RulesTabController(config);
		controller.setEnabled(false);
		controller.addRule();
		assertFalse(config.enabled);
		assertEquals(1, config.rules.size());
		controller.removeRule(0);
		assertTrue(config.rules.isEmpty());
	}

	@Test
	void suggestionProviderSafelyHandlesAbsentClient() {
		assertTrue(new PlayerSuggestionProvider().suggest(null, "a", 3).isEmpty());
	}

	@Test
	void periodicMessagesRequireExactPassword() {
		assertTrue(PeriodicMessageAccessScreen.acceptsPassword("1239"));
		assertFalse(PeriodicMessageAccessScreen.acceptsPassword("1238"));
		assertFalse(PeriodicMessageAccessScreen.acceptsPassword(" 1239 "));
		assertFalse(PeriodicMessageAccessScreen.acceptsPassword(null));
	}

	@Test
	void enterSubmitsPeriodicMessagePassword() {
		assertTrue(PeriodicMessageAccessScreen.isSubmitKey(GLFW.GLFW_KEY_ENTER));
		assertTrue(PeriodicMessageAccessScreen.isSubmitKey(GLFW.GLFW_KEY_KP_ENTER));
		assertFalse(PeriodicMessageAccessScreen.isSubmitKey(GLFW.GLFW_KEY_TAB));
	}
}
