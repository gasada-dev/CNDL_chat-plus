package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ChatHistoryStoreTest {
	@TempDir
	Path directory;

	@Test
	void savesAndLoadsEntriesInOrder() {
		ChatHistoryStore store = new ChatHistoryStore(directory);
		List<ChatHistoryEntry> entries = List.of(
				new ChatHistoryEntry(1L, "{\"text\":\"первое\"}"),
				new ChatHistoryEntry(2L, "{\"text\":\"второе\"}"));

		assertTrue(store.save("example.org_25565", entries));

		List<ChatHistoryEntry> loaded = store.load("example.org_25565");
		assertEquals(2, loaded.size());
		assertEquals(1L, loaded.get(0).timestamp());
		assertEquals("{\"text\":\"второе\"}", loaded.get(1).json());
		assertTrue(Files.exists(directory.resolve("example.org_25565.json")));
		assertFalse(Files.exists(directory.resolve("example.org_25565.json.tmp")));
	}

	@Test
	void loadReturnsEmptyForMissingOrCorruptFile() throws Exception {
		ChatHistoryStore store = new ChatHistoryStore(directory);
		assertTrue(store.load("missing").isEmpty());

		Files.writeString(directory.resolve("broken.json"), "{not json", StandardCharsets.UTF_8);
		assertTrue(store.load("broken").isEmpty());

		Files.writeString(directory.resolve("partial.json"),
				"[{\"timestamp\":1,\"json\":\"{}\"},null,{\"timestamp\":2}]",
				StandardCharsets.UTF_8);
		List<ChatHistoryEntry> loaded = store.load("partial");
		assertEquals(1, loaded.size());
		assertEquals(1L, loaded.getFirst().timestamp());
	}

	@Test
	void preservesCorruptBytesOnSaveEvenAfterRepeatedLoad() throws Exception {
		Path path = directory.resolve("broken.json");
		Files.writeString(path, "{not json");
		ChatHistoryStore store = new ChatHistoryStore(directory);
		store.load("broken");
		store.load("broken");
		assertTrue(store.save("broken", List.of(new ChatHistoryEntry(1, "{}"))));
		try (var files = Files.list(directory)) {
			List<Path> backups = files.filter(file -> file.toString().endsWith(".bak")).toList();
			assertEquals(1, backups.size());
			assertEquals("{not json", Files.readString(backups.getFirst()));
		}
		assertEquals(1, store.load("broken").size());
	}

	@Test
	void oversizedFileIsNotReadAndIsPreservedBeforeReplacement() throws Exception {
		Path path = directory.resolve("large.json");
		try (var file = new java.io.RandomAccessFile(path.toFile(), "rw")) {
			file.setLength((long) ChatHistoryStore.MAX_FILE_BYTES + 1);
		}
		ChatHistoryStore store = new ChatHistoryStore(directory);
		assertTrue(store.load("large").isEmpty());
		assertTrue(store.save("large", List.of()));
		try (var files = Files.list(directory)) {
			Path backup = files.filter(file -> file.toString().endsWith(".bak")).findFirst().orElseThrow();
			assertEquals((long) ChatHistoryStore.MAX_FILE_BYTES + 1, Files.size(backup));
		}
	}

	@Test
	void backupFailurePreventsReplacement() throws Exception {
		Path path = directory.resolve("broken.json");
		Files.writeString(path, "broken");
		ChatHistoryStore store = new ChatHistoryStore(directory);
		store.load("broken");
		Files.delete(path);
		Files.createDirectory(path);
		Files.writeString(path.resolve("keep"), "original");
		assertFalse(store.save("broken", List.of()));
		assertEquals("original", Files.readString(path.resolve("keep")));
	}

	@Test
	void fileKeySanitizesAddressCharacters() {
		assertEquals("example.org_25565", ChatHistoryStore.fileKey("example.org:25565"));
		assertEquals("_2001_db8__1__25565", ChatHistoryStore.fileKey("[2001:db8::1]:25565"));
		assertEquals("mc-server.example.org_25565",
				ChatHistoryStore.fileKey("mc-server.example.org:25565"));
	}
}
