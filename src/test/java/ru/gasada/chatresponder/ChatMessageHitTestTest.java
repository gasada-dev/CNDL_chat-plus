package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ChatMessageHitTestTest {
	@Test
	void mapsVisibleRowsAndScrollPosition() {
		assertEquals(3, ChatMessageHitTest.lineIndex(10, 159, 200, 320, 180, 1, 9, 3));
		assertEquals(4, ChatMessageHitTest.lineIndex(10, 150, 200, 320, 180, 1, 9, 3));
	}

	@Test
	void rejectsOutsideChatBounds() {
		assertEquals(-1, ChatMessageHitTest.lineIndex(3, 150, 200, 320, 180, 1, 9, 0));
		assertEquals(-1, ChatMessageHitTest.lineIndex(10, 161, 200, 320, 180, 1, 9, 0));
		assertEquals(-1, ChatMessageHitTest.lineIndex(10, -30, 200, 320, 180, 1, 9, 0));
	}
}
