package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ChatTextSelectionTest {
	private static final List<String> LINES = List.of("new", "middle", "old");
	private static final List<int[]> ADVANCES = List.of(new int[] {0, 10, 20, 30},
			new int[] {0, 10, 20, 30, 40, 50, 60}, new int[] {0, 10, 20, 30});

	@Test
	void mapsCoordinatesWithScrollAndClampsDraggedEndpoints() {
		assertEquals(new ChatTextSelection.Point(2, 2), ChatTextSelection.pointAt(24, 141, 200, 180,
				1, 9, 2, LINES, ADVANCES));
		assertEquals(new ChatTextSelection.Point(2, 0), ChatTextSelection.pointAt(-20, -30, 200, 180,
				1, 9, 2, LINES, ADVANCES));
		assertNull(ChatTextSelection.pointAt(4, 160, 200, 180, 0, 9, 0, LINES, ADVANCES));
	}

	@Test
	void identifiesChatBoundsBeforeStartingSelection() {
		assertTrue(ChatTextSelection.contains(10, 159, 200, 320, 180, 1, 9));
		assertFalse(ChatTextSelection.contains(3, 159, 200, 320, 180, 1, 9));
		assertFalse(ChatTextSelection.contains(10, 161, 200, 320, 180, 1, 9));
	}

	@Test
	void extractsMultipleVisualLinesInScreenOrder() {
		assertEquals("ol\nmiddle\new", ChatTextSelection.selectedText(LINES,
				new ChatTextSelection.Point(0, 1), new ChatTextSelection.Point(2, 2)));
	}

	@Test
	void reversesBottomToTopDragWithoutReversingCopiedLines() {
		assertEquals("ol\nmiddle\new", ChatTextSelection.selectedText(LINES,
				new ChatTextSelection.Point(2, 2), new ChatTextSelection.Point(0, 1)));
	}

	@Test
	void clampsOffsetsAndKeepsEmptyOrBlankSelectionsEmptyForCaller() {
		assertEquals("new", ChatTextSelection.selectedText(LINES,
				new ChatTextSelection.Point(0, -5), new ChatTextSelection.Point(0, 20)));
		assertEquals("", ChatTextSelection.selectedText(List.of("   "),
				new ChatTextSelection.Point(0, 1), new ChatTextSelection.Point(0, 1)));
		assertTrue(ChatTextSelection.selectedText(List.of("   "),
				new ChatTextSelection.Point(0, 0), new ChatTextSelection.Point(0, 3)).isBlank());
	}
}
