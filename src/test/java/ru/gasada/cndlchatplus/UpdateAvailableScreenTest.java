package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UpdateAvailableScreenTest {
	@Test
	void formatsMarkdownNotesForUpdateWindow() {
		assertEquals("Что изменилось\n• Добавлено первое\n• Добавлено второе",
				UpdateAvailableScreen.formatNotes("# Что изменилось\n\n- Добавлено первое\n- Добавлено второе\n"));
		assertEquals("Рекомендуется установить актуальную версию мода.",
				UpdateAvailableScreen.formatNotes("   "));
	}
}
