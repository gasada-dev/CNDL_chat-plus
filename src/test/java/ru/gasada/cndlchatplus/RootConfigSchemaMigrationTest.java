package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RootConfigSchemaMigrationTest {
	@TempDir Path directory;

	@Test
	void updatesSchemaWithoutChangingTemplateReferences() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		RootConfig root = new RootConfig();
		root.schemaVersion = 1;
		root.defaultTemplateId = "missing-template";
		root.serverBindings.put("bound.example:25565", "missing-template");
		assertTrue(repository.saveRoot(root).success());

		var result = new RootConfigSchemaMigration(repository).migrate();

		assertTrue(result.success());
		assertEquals(3, repository.loadRoot().value().schemaVersion);
		assertEquals("missing-template", repository.loadRoot().value().defaultTemplateId);
		assertEquals("missing-template", repository.loadRoot().value().serverBindings.get("bound.example:25565"));
	}
}
