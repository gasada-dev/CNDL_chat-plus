package ru.gasada.chatresponder;

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
		ServerTemplate game = repository.loadTemplate("game").value();
		assertEquals("mc.vanilla-game.ru", game.name);
		assertEquals("ignore {player}", game.commands.ignorePlayer);
		assertEquals("tpa {player}", game.commands.call);
		assertTrue(game.friends.isEmpty());
		assertTrue(game.friendLastSeen.isEmpty());
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
