package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ChatBookmarkStoreTest {
	@TempDir
	Path directory;
	private final AtomicLong clock = new AtomicLong(1_000L);

	@Test
	void addsRemovesClearsAndKeepsIdenticalMessagesIndependent() {
		ChatBookmarkStore store = store();
		store.connect(null, "Сессия");
		ChatBookmark first = store.add(ChatTab.PRIVATE, "Steve", "одинаково");
		clock.incrementAndGet();
		ChatBookmark second = store.add(ChatTab.PRIVATE, "Steve", "одинаково");

		assertNotEquals(first.id(), second.id());
		assertEquals(second.id(), store.snapshot().getFirst().id());
		assertTrue(store.remove(first.id()));
		assertEquals(1, store.snapshot().size());
		store.clear();
		assertTrue(store.snapshot().isEmpty());
	}

	@Test
	void persistsPerServerAndCleansTemporaryFile() {
		ChatBookmarkStore store = store();
		store.connect("one", "one.example:25565");
		store.add(ChatTab.GLOBAL, "Alex", "первый сервер");
		store.connect("two", "two.example:25565");
		assertTrue(store.snapshot().isEmpty());
		store.add(ChatTab.SYSTEM, null, "второй сервер");
		store.connect("one", "one.example:25565");

		assertEquals(1, store.snapshot().size());
		assertEquals("первый сервер", store.snapshot().getFirst().text());
		assertTrue(Files.exists(directory.resolve("one.json")));
		assertTrue(Files.exists(directory.resolve("two.json")));
		assertFalse(Files.exists(directory.resolve("one.json.tmp")));
	}

	@Test
	void corruptAndInvalidEntriesFailSafe() throws Exception {
		Files.writeString(directory.resolve("broken.json"), "{bad json", StandardCharsets.UTF_8);
		ChatBookmarkStore store = store();
		store.connect("broken", "broken");
		assertTrue(store.snapshot().isEmpty());

		Files.writeString(directory.resolve("partial.json"), """
				[
				  null,
				  {"id":"","savedAtMillis":1,"text":"bad"},
				  {"id":"same","savedAtMillis":2,"channel":"UNKNOWN","text":"first"},
				  {"id":"same","savedAtMillis":3,"channel":"GLOBAL","text":"duplicate"},
				  {"id":"valid","savedAtMillis":4,"sender":"   ","text":"[Alex head]visible"}
				]
				""", StandardCharsets.UTF_8);
		store.connect("partial", "partial");

		assertEquals(2, store.snapshot().size());
		assertEquals("visible", store.snapshot().getFirst().text());
		assertNull(store.snapshot().getFirst().sender());
		assertEquals(ChatTab.SYSTEM.name(), store.snapshot().get(1).channel());
	}

	@Test
	void corruptFileIsNotOverwrittenWithoutMutation() throws Exception {
		Path path = directory.resolve("broken.json");
		byte[] corrupt = "{broken".getBytes(StandardCharsets.UTF_8);
		Files.write(path, corrupt);
		ChatBookmarkStore store = store();
		store.connect("broken", "broken");
		store.disconnect();
		assertTrue(java.util.Arrays.equals(corrupt, Files.readAllBytes(path)));
	}

	@Test
	void corruptFileIsPreservedWhenNewBookmarkIsAdded() throws Exception {
		Path path = directory.resolve("broken.json");
		byte[] corrupt = "{broken".getBytes(StandardCharsets.UTF_8);
		Files.write(path, corrupt);
		ChatBookmarkStore store = store();
		store.connect("broken", "broken");

		store.add(ChatTab.LOCAL, null, "новая закладка");

		assertTrue(store.lastSaveSucceeded());
		ChatBookmarkStore reloaded = store();
		reloaded.connect("broken", "broken");
		assertEquals("новая закладка", reloaded.snapshot().getFirst().text());
		List<Path> backups;
		try (var files = Files.list(directory)) {
			backups = files.filter(file -> file.getFileName().toString().startsWith("broken.json.unreadable-"))
					.toList();
		}
		assertEquals(1, backups.size());
		assertArrayEquals(corrupt, Files.readAllBytes(backups.getFirst()));
	}

	@Test
	void failedWriteKeepsRuntimeDataAndReportsFailure() throws Exception {
		Path notDirectory = directory.resolve("file");
		Files.writeString(notDirectory, "occupied", StandardCharsets.UTF_8);
		ChatBookmarkStore store = new ChatBookmarkStore(notDirectory, clock::get);
		store.connect("server", "server");
		store.add(ChatTab.LOCAL, null, "важное");
		assertFalse(store.lastSaveSucceeded());
		assertEquals("важное", store.snapshot().getFirst().text());
	}

	@Test
	void updatesOnlyTextInPlaceAndPersistsAcrossReconnect() throws Exception {
		Files.writeString(directory.resolve("server.json"), """
				[
				  {"id":"newer","savedAtMillis":2000,"messageTimestampMillis":1900,
				   "channel":"PRIVATE","sender":"Steve","text":"старый текст"},
				  {"id":"older","savedAtMillis":1000,"channel":"GLOBAL","sender":"Alex","text":"другой"}
				]
				""", StandardCharsets.UTF_8);
		ChatBookmarkStore store = store();
		store.connect("server", "server");
		ChatBookmark expected = new ChatBookmark("newer", 2_000L, 1_900L, ChatTab.PRIVATE.name(), "Steve",
				"новый текст");

		assertTrue(store.updateText("newer", "  [Alex head]новый текст  "));
		assertEquals(expected, store.snapshot().getFirst());
		assertEquals("older", store.snapshot().get(1).id());
		assertTrue(store.lastSaveSucceeded());
		assertFalse(Files.exists(directory.resolve("server.json.tmp")));

		store.connect("other", "other");
		store.connect("server", "server");
		assertEquals(expected, store.snapshot().getFirst());
	}

	@Test
	void rejectsBlankAndUnknownUpdatesWithoutMutationOrSave() throws Exception {
		ChatBookmarkStore store = store();
		store.connect("server", "server");
		ChatBookmark bookmark = store.add(ChatTab.LOCAL, "Steve", "исходный");
		byte[] saved = Files.readAllBytes(directory.resolve("server.json"));

		assertFalse(store.updateText(bookmark.id(), "   "));
		assertFalse(store.updateText("unknown", "замена"));
		assertEquals(List.of(bookmark), store.snapshot());
		assertTrue(java.util.Arrays.equals(saved, Files.readAllBytes(directory.resolve("server.json"))));
	}

	@Test
	void failedUpdateWriteKeepsRuntimeEditAndReportsFailure() throws Exception {
		Path notDirectory = directory.resolve("file");
		Files.writeString(notDirectory, "occupied", StandardCharsets.UTF_8);
		ChatBookmarkStore store = new ChatBookmarkStore(notDirectory, clock::get);
		store.connect("server", "server");
		ChatBookmark bookmark = store.add(ChatTab.LOCAL, null, "до изменения");

		assertTrue(store.updateText(bookmark.id(), "после изменения"));
		assertEquals("после изменения", store.snapshot().getFirst().text());
		assertFalse(store.lastSaveSucceeded());
	}

	@Test
	void truncatesOversizedTextWithoutDroppingBookmarks() {
		ChatBookmarkStore store = store();
		store.connect(null, null);
		ChatBookmark oversized = store.add(ChatTab.LOCAL, "S".repeat(100),
				"x".repeat(ChatBookmarkStore.MAX_TEXT_LENGTH + 20));
		assertEquals(ChatBookmarkStore.MAX_TEXT_LENGTH, oversized.text().length());
		assertEquals(ChatBookmarkStore.MAX_SENDER_LENGTH, oversized.sender().length());
		int bookmarkCount = 5_003;
		for (int index = 0; index < bookmarkCount - 1; index++) {
			clock.incrementAndGet();
			store.add(ChatTab.LOCAL, null, "message " + index);
		}
		assertEquals(bookmarkCount, store.snapshot().size());
	}

	@Test
	void streamingLoadRetainsEveryEntryFromBoundedFile() throws Exception {
		StringBuilder json = new StringBuilder("[");
		int bookmarkCount = 5_020;
		for (int index = 0; index < bookmarkCount; index++) {
			if (index > 0) json.append(',');
			json.append("{\"id\":\"").append(index).append("\",\"savedAtMillis\":")
					.append(index + 1).append(",\"text\":\"message\"}");
		}
		json.append(']');
		Files.writeString(directory.resolve("large.json"), json, StandardCharsets.UTF_8);
		ChatBookmarkStore store = store();
		store.connect("large", "large");
		assertEquals(bookmarkCount, store.snapshot().size());
	}

	private ChatBookmarkStore store() {
		return new ChatBookmarkStore(directory, clock::get);
	}
}
