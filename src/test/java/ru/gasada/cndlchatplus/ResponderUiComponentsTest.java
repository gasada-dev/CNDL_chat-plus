package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

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
	void suggestionProviderSafelyHandlesAbsentClient() {
		assertTrue(new PlayerSuggestionProvider().suggest(null, "a", 3).isEmpty());
	}

	@Test
	void chatTabPagesFitWidthsAndAlwaysAdvancePastOversizedTabs() {
		assertEquals(List.of(0, 2), ChatTabBar.pageStarts(List.of(30, 30, 30), 62));
		assertNotEquals(ChatTabBar.pageStarts(List.of(30, 30, 30), 62),
				ChatTabBar.pageStarts(List.of(30, 36, 30), 62));
		assertEquals(List.of(0, 1), ChatTabBar.pageStarts(List.of(100, 20), 40));
		assertTrue(ChatTabBar.pageStarts(List.of(), 40).isEmpty());
	}

	@Test
	void chatTabOverflowMovesToActivePageAndKeepsCurrentPageWhenItContainsActive() {
		List<Integer> starts = List.of(0, 2, 4);
		assertEquals(1, ChatTabBar.pageContaining(starts, 5, 3, 0));
		assertEquals(1, ChatTabBar.pageContaining(starts, 5, 3, 1));
		assertEquals(2, ChatTabBar.pageContaining(starts, 5, -1, 2));
		assertEquals(2, ChatTabBar.pageContaining(starts, 5, 4, 99));
	}

	@Test
	void chatTabActiveIdentityDoesNotCollideOnSharedId() {
		ChatTabDefinition builtIn = ChatTabDefinition.builtIn(ChatTab.GLOBAL);
		ChatTabDefinition custom = new ChatTabDefinition(builtIn.id(), builtIn.displayName(), null,
				List.of(ChatTabSource.GLOBAL), "");
		assertFalse(ChatTabBar.isActive(builtIn, custom));
	}

	@Test
	void customTabValidationRequiresUniqueNameAndSourceButAllowsOwnName() {
		List<CustomChatTab> tabs = List.of(new CustomChatTab("voice", "ГС чат",
				List.of(ChatTabSource.VOICE), "/gc"));
		assertEquals("Введите название вкладки",
				ChatTabEditorValidation.error(" ", List.of(ChatTabSource.LOCAL), null, tabs));
		assertEquals("Выберите хотя бы один канал",
				ChatTabEditorValidation.error("Другое", List.of(), null, tabs));
		assertEquals("Вкладка с таким названием уже существует",
				ChatTabEditorValidation.error("гс ЧАТ", List.of(ChatTabSource.VOICE), null, tabs));
		assertNull(ChatTabEditorValidation.error("ГС чат", List.of(ChatTabSource.VOICE), "voice", tabs));
	}
}
