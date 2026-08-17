package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TemplateCatalogServiceTest {
	@TempDir Path directory;

	@Test
	void bundledTemplatesInstallOnceWithoutOverwritingUserData() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory.resolve("config"));
		TemplateCatalogService service = new TemplateCatalogService(repository, directory.resolve("imports"));

		TemplateCatalogService.ImportSummary first = service.installBundledTemplates();
		assertTrue(first.success(), first.errors().toString());
		assertEquals(2, first.installed());
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		TemplateSelectionService selection = new TemplateSelectionService(
				repository, runtime, ResponderConfig.defaults());
		assertTrue(selection.initializeDefault().success());
		assertEquals("vanilla-box", repository.loadRoot().value().defaultTemplateId);
		ServerTemplate vanilla = repository.loadTemplate("vanilla-box").value();
		assertEquals("w {player} {message}", vanilla.commands.privateMessage);
		assertEquals("tpaccept", vanilla.commands.acceptTeleport);
		assertFalse(vanilla.parsers.teleportRequestPattern.isBlank());
		ServerTemplate game = repository.loadTemplate("vanilla-game").value();
		assertEquals("Vanilla-game", game.name);
		assertEquals("ignore {player}", game.commands.ignorePlayer);
		assertEquals("tpa {player}", game.commands.call);
		assertEquals("marry list {page}", game.commands.marriageList);
		assertEquals("tpaccept", game.commands.acceptTeleport);
		assertFalse(game.parsers.teleportRequestPattern.isBlank());
		assertFalse(game.parsers.marriageEntryPattern.isBlank());
		assertTrue(game.playerInfo.marriageLookupConfigured);
		assertEquals(PlayerInfoProvider.VANILLA_GAME_PUBLIC_API, game.playerInfo.provider);
		assertTrue(game.friends.isEmpty());
		assertTrue(game.friendLastSeen.isEmpty());
		RootConfig root = repository.loadRoot().value();
		assertEquals(java.util.List.of("mc.vanilla-box.ru:25565"), root.templates.stream()
				.filter(info -> info.id.equals("vanilla-box")).findFirst().orElseThrow().addressPatterns);
		assertEquals(java.util.List.of("mc.vanilla-game.ru:25565"), root.templates.stream()
				.filter(info -> info.id.equals("vanilla-game")).findFirst().orElseThrow().addressPatterns);
		assertEquals("vanilla-box", new ServerTemplateResolver().resolve(root, "MC.VANILLA-BOX.RU").templateId());
		assertEquals("vanilla-game", new ServerTemplateResolver().resolve(root, "mc.vanilla-game.ru:25565").templateId());
		vanilla.commands.privateMessage = "msg {player} {message}";
		assertTrue(repository.saveTemplate(vanilla).success());

		TemplateCatalogService.ImportSummary second = service.installBundledTemplates();
		assertTrue(second.success());
		assertEquals(0, second.installed());
		assertEquals(2, second.skipped());
		assertEquals("msg {player} {message}",
				repository.loadTemplate("vanilla-box").value().commands.privateMessage);
	}

	@Test
	void bundledCatalogAddsDomainsWithoutReplacingExistingTemplates() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory.resolve("existing"));
		ServerTemplate vanilla = ServerTemplate.empty("vanilla-box", "Custom Vanilla");
		vanilla.commands.privateMessage = "msg {player} {message}";
		vanilla.commands.marriageList = "marry list {page}";
		ParserSettings.applyVanillaGameMarriageDefaults(vanilla.parsers);
		vanilla.playerInfo.marriageLookupConfigured = true;
		ServerTemplate game = ServerTemplate.empty("vanilla-game", "Custom Game");
		game.commands.call = "tpask {player}";
		game.commands.marriageList = "weddings {page}";
		game.parsers.marriageEntryPattern = "([A-Za-z0-9_]+) & ([A-Za-z0-9_]+)";
		assertTrue(repository.saveTemplate(vanilla).success());
		assertTrue(repository.saveTemplate(game).success());
		RootConfig root = new RootConfig();
		root.templates.add(new ServerTemplateInfo(vanilla.id, vanilla.name));
		root.templates.add(new ServerTemplateInfo(game.id, game.name));
		assertTrue(repository.saveRoot(root).success());

		TemplateCatalogService.ImportSummary result = new TemplateCatalogService(
				repository, directory.resolve("imports-existing")).installBundledTemplates();

		assertTrue(result.success(), result.errors().toString());
		assertEquals(2, result.skipped());
		assertEquals("msg {player} {message}", repository.loadTemplate("vanilla-box").value().commands.privateMessage);
		assertEquals("tpaccept", repository.loadTemplate("vanilla-box").value().commands.acceptTeleport);
		assertFalse(repository.loadTemplate("vanilla-box").value().parsers.teleportRequestPattern.isBlank());
		assertTrue(repository.loadTemplate("vanilla-box").value().commands.marriageList.isBlank());
		assertTrue(repository.loadTemplate("vanilla-box").value().parsers.marriageEntryPattern.isBlank());
		assertFalse(repository.loadTemplate("vanilla-box").value().playerInfo.marriageLookupConfigured);
		assertEquals("tpask {player}", repository.loadTemplate("vanilla-game").value().commands.call);
		assertEquals("weddings {page}",
				repository.loadTemplate("vanilla-game").value().commands.marriageList);
		assertEquals("tpaccept", repository.loadTemplate("vanilla-game").value().commands.acceptTeleport);
		assertFalse(repository.loadTemplate("vanilla-game").value().parsers.teleportRequestPattern.isBlank());
		assertEquals("([A-Za-z0-9_]+) & ([A-Za-z0-9_]+)",
				repository.loadTemplate("vanilla-game").value().parsers.marriageEntryPattern);
		assertFalse(repository.loadTemplate("vanilla-game").value().parsers.marriagePagePattern.isBlank());
		assertEquals(PlayerInfoProvider.VANILLA_GAME_PUBLIC_API,
				repository.loadTemplate("vanilla-game").value().playerInfo.provider);
		assertTrue(repository.loadTemplate("vanilla-game").value().playerInfo.providerConfigured);
		RootConfig saved = repository.loadRoot().value();
		assertEquals(java.util.List.of("mc.vanilla-box.ru:25565"), saved.templates.get(0).addressPatterns);
		assertEquals(java.util.List.of("mc.vanilla-game.ru:25565"), saved.templates.get(1).addressPatterns);
	}

	@Test
	void userFolderImportsValidJsonAndRejectsUnsafeOrInvalidTemplates() throws Exception {
		Path config = directory.resolve("config");
		Path imports = directory.resolve("imports");
		Files.createDirectories(imports);
		ServerTemplateRepository repository = new ServerTemplateRepository(config);
		TemplateCatalogService service = new TemplateCatalogService(repository, imports);

		ServerTemplate valid = ServerTemplate.empty("example-server", "Example server");
		valid.commands.privateMessage = "msg {player} {message}";
		valid.commands.call = "tpa {player}";
		valid.commands.ignorePlayer = "ignore {player}";
		valid.parsers.discordMarkerPattern = "(?iu)\\[bridge]";
		valid.parsers.discordNamePattern = "[\\p{L}\\p{N}_ ]{1,32}";
		Files.writeString(imports.resolve("example.json"), new Gson().toJson(valid), StandardCharsets.UTF_8);

		ServerTemplate invalid = ServerTemplate.empty("../escape", "Unsafe");
		Files.writeString(imports.resolve("unsafe.json"), new Gson().toJson(invalid), StandardCharsets.UTF_8);
		TemplateCatalogService.ImportSummary result = service.importUserTemplates();

		assertEquals(1, result.installed());
		assertFalse(result.success());
		assertEquals("msg {player} {message}",
				repository.loadTemplate("example-server").value().commands.privateMessage);
		assertFalse(repository.loadTemplate("../escape").success());
		assertTrue(result.errors().stream().anyMatch(error -> error.contains("unsafe.json")));
	}
}
