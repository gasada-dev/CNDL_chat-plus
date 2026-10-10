package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UpdateAvailableScreenTest {
	@Test
	void keepsThirdDownloadCenteredOnItsOwnRow() {
		UpdateAvailableScreen.DownloadLayout layout = UpdateAvailableScreen.downloadLayout(296, 178);

		assertEquals(118, layout.buttonWidth());
		assertEquals(24, layout.minecraft12111X());
		assertEquals(154, layout.minecraft262X());
		assertEquals(89, layout.minecraft263X());
		assertEquals(118, layout.firstRowY());
		assertEquals(148, layout.secondRowY());
		assertEquals(296, layout.minecraft263X() * 2 + layout.buttonWidth());
		assertEquals(12, layout.minecraft262X() - layout.minecraft12111X() - layout.buttonWidth());
	}

	@Test
	void formatsMarkdownNotesForUpdateWindow() {
		assertEquals("Что изменилось\n• Добавлено первое\n• Добавлено второе",
				UpdateAvailableScreen.formatNotes("# Что изменилось\n\n- Добавлено первое\n- Добавлено второе\n"));
		assertEquals("Рекомендуется установить актуальную версию мода.",
				UpdateAvailableScreen.formatNotes("   "));
	}
}
