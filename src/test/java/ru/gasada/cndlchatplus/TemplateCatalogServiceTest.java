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
		assertEquals(1, first.installed());
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		TemplateSelectionService selection = new TemplateSelectionService(
				repository, runtime, ResponderConfig.defaults());
		assertTrue(selection.initializeDefault().success());
		assertEquals("vanilla-box", repository.loadRoot().value().defaultTemplateId);
		ServerTemplate vanilla = repository.loadTemplate("vanilla-box").value();
		assertEquals("w {player} {message}", vanilla.commands.privateMessage);
		assertEquals("tpaccept", vanilla.commands.acceptTeleport);
		assertEquals("ps add {player}", vanilla.commands.protectionAdd);
		assertEquals("ps remove {player}", vanilla.commands.protectionRemove);
		assertEquals("vm trusted add {player}", vanilla.commands.traderTrustedAdd);
		assertEquals("vm trusted remove {player}", vanilla.commands.traderTrustedRemove);
		assertEquals("claimfly", vanilla.commands.claimFly);
		assertEquals("enderchest", vanilla.commands.enderChest);
		assertEquals("marry kiss", vanilla.commands.marryKiss);
		assertEquals("marry home", vanilla.commands.marryHome);
		assertEquals("marry tp", vanilla.commands.marryTp);
		assertTrue(vanilla.commands.nearbyPlayerCommandsConfigured);
		assertTrue(vanilla.commands.traderTrustedRemoveConfigured);
		assertTrue(vanilla.commands.utilityCommandsConfigured);
		assertFalse(vanilla.parsers.teleportRequestPattern.isBlank());
		assertEquals(TeleportAutoAcceptMode.OFF, vanilla.teleportAutoAcceptMode);
		assertTrue(vanilla.teleportAutoAcceptFriends.isEmpty());
		RootConfig root = repository.loadRoot().value();
		assertEquals(java.util.List.of("mc.vanilla-box.ru:25565"), root.templates.stream()
				.filter(info -> info.id.equals("vanilla-box")).findFirst().orElseThrow().addressPatterns);
		assertEquals("vanilla-box", new ServerTemplateResolver().resolve(root, "MC.VANILLA-BOX.RU").templateId());
		vanilla.commands.privateMessage = "msg {player} {message}";
		assertTrue(repository.saveTemplate(vanilla).success());

		TemplateCatalogService.ImportSummary second = service.installBundledTemplates();
		assertTrue(second.success());
		assertEquals(0, second.installed());
		assertEquals(1, second.skipped());
		assertEquals("msg {player} {message}",
				repository.loadTemplate("vanilla-box").value().commands.privateMessage);
	}

	@Test
	void bundledCatalogAddsDomainsWithoutReplacingExistingTemplates() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory.resolve("existing"));
		ServerTemplate vanilla = ServerTemplate.empty("vanilla-box", "Custom Vanilla");
		vanilla.commands.privateMessage = "msg {player} {message}";
		assertTrue(repository.saveTemplate(vanilla).success());
		RootConfig root = new RootConfig();
		root.templates.add(new ServerTemplateInfo(vanilla.id, vanilla.name));
		assertTrue(repository.saveRoot(root).success());

		TemplateCatalogService.ImportSummary result = new TemplateCatalogService(
				repository, directory.resolve("imports-existing")).installBundledTemplates();

		assertTrue(result.success(), result.errors().toString());
		assertEquals(1, result.skipped());
		assertEquals("msg {player} {message}", repository.loadTemplate("vanilla-box").value().commands.privateMessage);
		assertEquals("tpaccept", repository.loadTemplate("vanilla-box").value().commands.acceptTeleport);
		assertEquals("ps add {player}", repository.loadTemplate("vanilla-box").value().commands.protectionAdd);
		assertEquals("ps remove {player}", repository.loadTemplate("vanilla-box").value().commands.protectionRemove);
		assertEquals("vm trusted add {player}",
				repository.loadTemplate("vanilla-box").value().commands.traderTrustedAdd);
		assertEquals("vm trusted remove {player}",
				repository.loadTemplate("vanilla-box").value().commands.traderTrustedRemove);
		assertFalse(repository.loadTemplate("vanilla-box").value().parsers.teleportRequestPattern.isBlank());
		RootConfig saved = repository.loadRoot().value();
		assertEquals(java.util.List.of("mc.vanilla-box.ru:25565"), saved.templates.get(0).addressPatterns);
	}

	@Test
	void nearbyPlayerDefaultsUpgradeOnceWithoutOverwritingCustomOrClearedValues() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory.resolve("nearby-upgrade"));
		ServerTemplate vanilla = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		vanilla.commands.protectionAdd = "custom add {player}";
		assertTrue(repository.saveTemplate(vanilla).success());
		RootConfig root = new RootConfig();
		root.templates.add(new ServerTemplateInfo(vanilla.id, vanilla.name));
		assertTrue(repository.saveRoot(root).success());
		TemplateCatalogService service = new TemplateCatalogService(repository, directory.resolve("unused-imports"));

		assertTrue(service.installBundledTemplates().success());
		ServerTemplate upgraded = repository.loadTemplate("vanilla-box").value();
		assertEquals("custom add {player}", upgraded.commands.protectionAdd);
		assertEquals("ps remove {player}", upgraded.commands.protectionRemove);
		assertEquals("vm trusted add {player}", upgraded.commands.traderTrustedAdd);
		assertEquals("vm trusted remove {player}", upgraded.commands.traderTrustedRemove);
		assertTrue(upgraded.commands.nearbyPlayerCommandsConfigured);
		assertTrue(upgraded.commands.traderTrustedRemoveConfigured);

		upgraded.commands.protectionRemove = "";
		upgraded.commands.traderTrustedRemove = "";
		assertTrue(repository.saveTemplate(upgraded).success());
		assertTrue(service.installBundledTemplates().success());
		assertTrue(repository.loadTemplate("vanilla-box").value().commands.protectionRemove.isBlank());
		assertTrue(repository.loadTemplate("vanilla-box").value().commands.traderTrustedRemove.isBlank());
	}

	@Test
	void utilityCommandDefaultsUpgradeVanillaBoxOnceAndLeaveOtherTemplatesBlank() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory.resolve("utility-upgrade"));
		ServerTemplate vanilla = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		vanilla.commands.claimFly = "customfly";
		ServerTemplate other = ServerTemplate.empty("other", "Other");
		assertTrue(repository.saveTemplate(vanilla).success());
		assertTrue(repository.saveTemplate(other).success());
		RootConfig root = new RootConfig();
		root.templates.add(new ServerTemplateInfo(vanilla.id, vanilla.name));
		root.templates.add(new ServerTemplateInfo(other.id, other.name));
		assertTrue(repository.saveRoot(root).success());
		TemplateCatalogService service = new TemplateCatalogService(repository, directory.resolve("utility-imports"));

		assertTrue(service.installBundledTemplates().success());
		ServerTemplate upgraded = repository.loadTemplate("vanilla-box").value();
		assertEquals("customfly", upgraded.commands.claimFly);
		assertEquals("enderchest", upgraded.commands.enderChest);
		assertEquals("marry kiss", upgraded.commands.marryKiss);
		assertEquals("marry home", upgraded.commands.marryHome);
		assertEquals("marry tp", upgraded.commands.marryTp);
		assertTrue(upgraded.commands.utilityCommandsConfigured);
		assertTrue(repository.loadTemplate("other").value().commands.claimFly.isBlank());
		assertTrue(repository.loadTemplate("other").value().commands.marryTp.isBlank());

		upgraded.commands.enderChest = "";
		assertTrue(repository.saveTemplate(upgraded).success());
		assertTrue(service.installBundledTemplates().success());
		assertTrue(repository.loadTemplate("vanilla-box").value().commands.enderChest.isBlank());
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
