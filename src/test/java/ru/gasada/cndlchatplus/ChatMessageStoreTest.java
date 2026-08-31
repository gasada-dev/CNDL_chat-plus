package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

final class ChatMessageStoreTest {
	@Test
	void keepsInsertionOrderAndSnapshotIsCopy() {
		ChatMessageStore store = new ChatMessageStore(() -> 10);
		store.add(new ChatHistoryEntry(1L, "{\"text\":\"a\"}"));
		store.add(new ChatHistoryEntry(2L, "{\"text\":\"b\"}"));

		List<ChatHistoryEntry> snapshot = store.snapshot();
		assertEquals(2, snapshot.size());
		assertEquals(1L, snapshot.get(0).timestamp());
		assertEquals(2L, snapshot.get(1).timestamp());

		store.clear();
		assertEquals(2, snapshot.size());
		assertTrue(store.snapshot().isEmpty());
	}

	@Test
	void dropsOldestEntriesAboveLimit() {
		AtomicInteger limit = new AtomicInteger(3);
		ChatMessageStore store = new ChatMessageStore(limit::get);
		for (int index = 1; index <= 5; index++) {
			store.add(new ChatHistoryEntry(index, "{\"text\":\"" + index + "\"}"));
		}

		List<ChatHistoryEntry> snapshot = store.snapshot();
		assertEquals(3, snapshot.size());
		assertEquals(3L, snapshot.get(0).timestamp());
		assertEquals(5L, snapshot.get(2).timestamp());
	}

	@Test
	void appliesReducedLimitOnNextAdd() {
		AtomicInteger limit = new AtomicInteger(5);
		ChatMessageStore store = new ChatMessageStore(limit::get);
		for (int index = 1; index <= 5; index++) {
			store.add(new ChatHistoryEntry(index, "{}"));
		}
		limit.set(2);
		store.add(new ChatHistoryEntry(6, "{}"));

		List<ChatHistoryEntry> snapshot = store.snapshot();
		assertEquals(2, snapshot.size());
		assertEquals(5L, snapshot.get(0).timestamp());
		assertEquals(6L, snapshot.get(1).timestamp());
	}

	@Test
	void replacesLatestEntryWithoutChangingItsTabOrSize() {
		ChatMessageStore store = new ChatMessageStore(() -> 10);
		store.add(new ChatHistoryEntry(1L, "first", ChatTab.LOCAL));
		store.add(new ChatHistoryEntry(2L, "duplicate", ChatTab.GLOBAL));

		assertTrue(store.replaceLast("duplicate", 3L, "counted"));
		assertEquals(List.of(
				new ChatHistoryEntry(1L, "first", ChatTab.LOCAL),
				new ChatHistoryEntry(3L, "counted", ChatTab.GLOBAL)), store.snapshot());
		assertFalse(store.replaceLast("wrong", 4L, "lost"));
		assertEquals("counted", store.snapshot().getLast().json());
	}
}
