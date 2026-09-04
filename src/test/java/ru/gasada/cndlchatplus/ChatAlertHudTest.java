package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

final class ChatAlertHudTest {
	@Test
	void boundsNoticesExpiresThemAndPlaysOneSoundPerDecision() {
		AtomicLong clock = new AtomicLong(1_000L);
		AtomicInteger sounds = new AtomicInteger();
		ChatAlertHud hud = new ChatAlertHud(clock::get, sounds::incrementAndGet);
		for (int index = 0; index < 4; index++) {
			hud.handle(new ChatAlertDecision(List.of("rule-" + index), true, true),
					"message " + index);
		}
		assertEquals(3, hud.noticeTexts().size());
		assertEquals("message 1", hud.noticeTexts().getFirst());

		hud.tickRuntime(true);
		assertEquals(4, sounds.get());
		clock.addAndGet(ChatAlertHud.NOTICE_MILLIS);
		hud.tickRuntime(true);
		assertTrue(hud.noticeTexts().isEmpty());
	}

	@Test
	void disconnectClearsNoticesAndPendingSound() {
		AtomicInteger sounds = new AtomicInteger();
		ChatAlertHud hud = new ChatAlertHud(() -> 1_000L, sounds::incrementAndGet);
		hud.handle(new ChatAlertDecision(List.of("rule"), true, true), "message");
		hud.tickRuntime(false);
		assertTrue(hud.noticeTexts().isEmpty());
		assertEquals(0, sounds.get());
	}
}
