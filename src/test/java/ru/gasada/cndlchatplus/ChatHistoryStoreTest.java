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
	void fileKeySanitizesAddressCharacters() {
		assertEquals("example.org_25565", ChatHistoryStore.fileKey("example.org:25565"));
		assertEquals("_2001_db8__1__25565", ChatHistoryStore.fileKey("[2001:db8::1]:25565"));
		assertEquals("mc-server.example.org_25565",
				ChatHistoryStore.fileKey("mc-server.example.org:25565"));
	}
}
