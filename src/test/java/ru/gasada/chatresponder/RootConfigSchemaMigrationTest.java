package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RootConfigSchemaMigrationTest {
	@TempDir Path directory;

	@Test
	void renamesGameAndPreservesDataDefaultAndBindings() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplate game = ServerTemplate.empty("game", "Мой Game");
		game.friends.add("Alice");
		game.friendLastSeen.put("Alice", "вчера");
		game.commands.privateMessage = "msg {player} {message}";
		assertTrue(repository.saveTemplate(game).success());
		RootConfig root = new RootConfig();
		root.schemaVersion = 1;
		ServerTemplateInfo info = new ServerTemplateInfo("game", "Мой Game");
		info.addressPatterns.add("old.example:25565");
		root.templates.add(info);
		root.defaultTemplateId = "game";
		root.serverBindings.put("bound.example:25565", "game");
		assertTrue(repository.saveRoot(root).success());

		TemplateOperationResult<RootConfigSchemaMigration.MigrationReport> result =
				new RootConfigSchemaMigration(repository).migrate();

		assertTrue(result.success());
		assertTrue(result.value().migrated());
		assertFalse(repository.loadTemplate("game").success());
		ServerTemplate migrated = repository.loadTemplate("vanilla-game").value();
		assertEquals(List.of("Alice"), migrated.friends);
		assertEquals("вчера", migrated.friendLastSeen.get("Alice"));
		assertEquals("msg {player} {message}", migrated.commands.privateMessage);
		assertEquals(PlayerInfoProvider.VANILLA_GAME_PUBLIC_API, migrated.playerInfo.provider);
		assertTrue(migrated.playerInfo.providerConfigured);
		RootConfig migratedRoot = repository.loadRoot().value();
		assertEquals(3, migratedRoot.schemaVersion);
		assertEquals("vanilla-game", migratedRoot.defaultTemplateId);
		assertEquals("vanilla-game", migratedRoot.serverBindings.get("bound.example:25565"));
		assertEquals(List.of("old.example:25565"), migratedRoot.templates.getFirst().addressPatterns);
		assertFalse(new RootConfigSchemaMigration(repository).migrate().value().migrated());
	}

	@Test
	void conflictNeverOverwritesEitherTemplate() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplate old = ServerTemplate.empty("game", "Old");
		old.friends.add("OldFriend");
		ServerTemplate current = ServerTemplate.empty("vanilla-game", "Current");
		current.friends.add("CurrentFriend");
		assertTrue(repository.saveTemplate(old).success());
		assertTrue(repository.saveTemplate(current).success());
		RootConfig root = new RootConfig();
		root.schemaVersion = 1;
		root.templates.add(new ServerTemplateInfo("game", "Old"));
		root.templates.add(new ServerTemplateInfo("vanilla-game", "Current"));
		assertTrue(repository.saveRoot(root).success());

		var result = new RootConfigSchemaMigration(repository).migrate();

		assertTrue(result.success());
		assertFalse(result.value().warnings().isEmpty());
		assertEquals(List.of("OldFriend"), repository.loadTemplate("game").value().friends);
		assertEquals(List.of("CurrentFriend"), repository.loadTemplate("vanilla-game").value().friends);
	}
}
