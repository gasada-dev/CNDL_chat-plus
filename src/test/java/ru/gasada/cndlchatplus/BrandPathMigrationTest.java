package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BrandPathMigrationTest {
	@TempDir
	Path directory;

	@Test
	void copiesLegacyDataWithoutDeletingSourcesOrReplacingNewFiles() throws Exception {
		Path oldConfig = write("gasada-chat-responder.json", "old config");
		Path oldImport = write("gasada-chat-responder-template-imports/template.json", "old import");
		Path oldHistory = write("gasada-chat-responder-chat-history/server.json", "old history");
		Path newHistory = write("cndl-chat-plus-chat-history/server.json", "new history");

		BrandPathMigration.migrate(directory);
		BrandPathMigration.migrate(directory);

		assertEquals("old config", Files.readString(directory.resolve("cndl-chat-plus.json")));
		assertEquals("old import", Files.readString(
				directory.resolve("cndl-chat-plus-template-imports/template.json")));
		assertEquals("new history", Files.readString(newHistory));
		assertTrue(Files.exists(oldConfig));
		assertTrue(Files.exists(oldImport));
		assertTrue(Files.exists(oldHistory));
	}

	private Path write(String relativePath, String content) throws Exception {
		Path path = directory.resolve(relativePath);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
		return path;
	}
}
