package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UpdateAvailableScreenTest {
	@Test
	void keepsThirdDownloadCenteredOnItsOwnRow() {
		UpdateAvailableScreen.DownloadLayout layout = UpdateAvailableScreen.downloadLayout(296, 178);

		assertTrue(layout.buttonWidth() > 0);
		assertTrue(layout.minecraft12111X() >= 0);
		assertTrue(layout.minecraft262X() + layout.buttonWidth() <= 296);
		assertEquals(296, layout.minecraft263X() * 2 + layout.buttonWidth());
		assertTrue(layout.minecraft262X() > layout.minecraft12111X() + layout.buttonWidth());
		assertTrue(layout.secondRowY() > layout.firstRowY() + 20);
	}

	@Test
	void formatsMarkdownNotesForUpdateWindow() {
		assertEquals("Что изменилось\n• Добавлено первое\n• Добавлено второе",
				UpdateAvailableScreen.formatNotes("# Что изменилось\n\n- Добавлено первое\n- Добавлено второе\n"));
		assertEquals("Рекомендуется установить актуальную версию мода.",
				UpdateAvailableScreen.formatNotes("   "));
	}
}
