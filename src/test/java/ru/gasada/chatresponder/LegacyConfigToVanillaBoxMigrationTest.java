package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LegacyConfigToVanillaBoxMigrationTest {
	@TempDir
	Path directory;

	@Test
	void migratesEveryLegacyServerCategoryWithoutLoss() throws IOException {
		Path legacy = copyFixture();
		byte[] original = Files.readAllBytes(legacy);
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		LegacyConfigToVanillaBoxMigration migration =
				new LegacyConfigToVanillaBoxMigration(legacy, repository);

		TemplateOperationResult<ServerTemplate> result = migration.migrateIfNeeded();
		assertTrue(result.success());
		ServerTemplate template = result.value();
		assertFalse(template.responderEnabled);
		assertEquals(2, template.rules.size());
		assertEquals("привет*", template.rules.getFirst().trigger);
		assertEquals("!!", template.globalPrefix);
		assertEquals("/cc", template.clanReplyPrefix);
		assertEquals("/reply", template.privateReplyCommand);
		assertEquals(2, template.mutedWords.size());
		assertEquals(2, template.discordMutedPlayers.size());
		assertEquals(2, template.friends.size());
		assertEquals("вчера", template.friendLastSeen.get("Alice"));
		assertFalse(template.friendHudEnabled);
		assertEquals(2, template.periodicMessages.size());
		assertEquals("clan lookup {player}", template.commands.lookupFriend);
		assertFalse(template.parsers.lookupOutputPattern.isBlank());

		Path backup = directory.resolve(LegacyConfigToVanillaBoxMigration.BACKUP_FILE_NAME);
		assertArrayEquals(original, Files.readAllBytes(backup));
		assertArrayEquals(original, Files.readAllBytes(legacy));
		assertFalse(Files.exists(directory.resolve(ServerTemplateRepository.ROOT_FILE_NAME + ".tmp")));
	}

	@Test
	void repeatedMigrationIsIdempotentAndDoesNotDuplicateVanillaBox() throws IOException {
		Path legacy = copyFixture();
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		LegacyConfigToVanillaBoxMigration migration =
				new LegacyConfigToVanillaBoxMigration(legacy, repository);
		assertTrue(migration.migrateIfNeeded().success());
		assertTrue(migration.migrateIfNeeded().success());

		RootConfig root = repository.loadRoot().value();
		assertEquals(1, root.templates.stream()
				.filter(info -> LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID.equals(info.id)).count());
		assertEquals(LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID, root.defaultTemplateId);
	}

	@Test
	void corruptedLegacyConfigIsBackedUpAndDoesNotCreateCompletedMigration() throws IOException {
		Path legacy = directory.resolve("gasada-chat-responder.json");
		Files.writeString(legacy, "{ broken json");
		byte[] original = Files.readAllBytes(legacy);
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);

		TemplateOperationResult<ServerTemplate> result =
				new LegacyConfigToVanillaBoxMigration(legacy, repository).migrateIfNeeded();
		assertFalse(result.success());
		assertArrayEquals(original, Files.readAllBytes(legacy));
		assertArrayEquals(original, Files.readAllBytes(
				directory.resolve(LegacyConfigToVanillaBoxMigration.BACKUP_FILE_NAME)));
		assertTrue(repository.loadRoot().value().templates.isEmpty());
		assertFalse(repository.loadTemplate(LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID).success());
	}

	private Path copyFixture() throws IOException {
		Path target = directory.resolve("gasada-chat-responder.json");
		try (InputStream input = getClass().getResourceAsStream("/fixtures/legacy-config.json")) {
			if (input == null) {
				throw new IOException("fixture not found");
			}
			Files.copy(input, target);
		}
		return target;
	}
}
