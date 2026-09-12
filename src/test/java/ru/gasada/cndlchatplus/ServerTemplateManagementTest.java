package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServerTemplateManagementTest {
	@TempDir Path directory;

	@Test
	void draftRenameAndPatternsDoNotChangeRuntimeUntilVanillaBoxSelection() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplateManager manager = new ServerTemplateManager(repository);
		ServerTemplate vanilla = manager.createEmpty("vanilla-box", "Vanilla-box").value();
		vanilla.commands = ServerCommandSettings.vanillaBoxDefaults();
		vanilla.parsers = ParserSettings.vanillaBoxDefaults();
		assertTrue(repository.saveTemplate(vanilla).success());
		assertTrue(manager.setDefault(vanilla.id).success());
		ServerTemplate copy = manager.copy("vanilla-box", "second", "Second").value();
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(copy);
		TemplateSelectionService selection = new TemplateSelectionService(repository, runtime, new ResponderConfig());

		ServerTemplate draft = copy.deepCopy(copy.id, copy.name);
		assertTrue(manager.saveDraft(draft, "Renamed",
				List.of("PLAY.Example.org", "*.example.org")).success());
		assertEquals("Second", runtime.activeSnapshot().orElseThrow().name());
		assertEquals("Renamed", repository.loadTemplate("second").value().name);
		assertEquals(List.of("play.example.org:25565", "*.example.org:25565"),
				repository.loadRoot().value().templates.stream()
						.filter(info -> "second".equals(info.id)).findFirst().orElseThrow().addressPatterns);
		assertTrue(selection.select("second").success());
		assertEquals("vanilla-box", runtime.activeSnapshot().orElseThrow().id());
		assertEquals("Vanilla-box", runtime.activeSnapshot().orElseThrow().name());
	}

	@Test
	void defaultBindingAndDeletionProtectionsArePersisted() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplateManager manager = new ServerTemplateManager(repository);
		assertTrue(manager.createEmpty("one", "One").success());
		assertTrue(manager.createEmpty("two", "Two").success());
		assertTrue(manager.setDefault("one").success());
		assertTrue(manager.bindAddress("Example.ORG", "two").success());
		assertFalse(manager.delete("one", "two").success());
		assertFalse(manager.delete("two", "two").success());
		assertTrue(manager.setDefault("two").success());
		assertTrue(manager.delete("one", "two").success());
		RootConfig root = repository.loadRoot().value();
		assertEquals("two", root.defaultTemplateId);
		assertEquals("two", root.serverBindings.get("example.org:25565"));
		assertFalse(repository.loadTemplate("one").success());
	}

	@Test
	void initializationCreatesVanillaBoxForFreshConfiguration() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		TemplateSelectionService selection = new TemplateSelectionService(
				repository, runtime, ResponderConfig.defaults());
		assertTrue(selection.initializeDefault().success());
		assertEquals("vanilla-box", runtime.activeSnapshot().orElseThrow().id());
		assertEquals("vanilla-box", repository.loadRoot().value().defaultTemplateId);
		assertTrue(repository.loadTemplate("vanilla-box").success());
	}

	@Test
	void initializationPinsConfiguredDefaultToVanillaBox() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplateManager manager = new ServerTemplateManager(repository);
		assertTrue(manager.createEmpty("vanilla-box", "Vanilla-box").success());
		assertTrue(manager.createEmpty("second", "Second").success());
		assertTrue(manager.setDefault("second").success());
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		TemplateSelectionService selection = new TemplateSelectionService(repository, runtime,
				ResponderConfig.defaults());

		assertTrue(selection.initializeDefault().success());
		assertEquals("vanilla-box", runtime.activeSnapshot().orElseThrow().id());
		assertEquals("second", repository.loadRoot().value().defaultTemplateId);
	}

	@Test
	void failedSelectionClearsRuntimeWithoutChangingConfiguredDefault() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplate selected = ServerTemplate.empty("selected", "Selected");
		assertTrue(repository.saveTemplate(selected).success());
		RootConfig root = new RootConfig();
		root.defaultTemplateId = "missing";
		assertTrue(repository.saveRoot(root).success());
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(selected);
		TemplateSelectionService selection = new TemplateSelectionService(repository, runtime, new ResponderConfig());

		assertFalse(selection.select("missing").success());
		assertTrue(runtime.activeSnapshot().isEmpty());
		assertEquals("missing", repository.loadRoot().value().defaultTemplateId);
	}

	@Test
	void explicitConnectionLifecycleSelectsVanillaBoxAndClearsOnDisconnect() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplate vanilla = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		assertTrue(repository.saveTemplate(vanilla).success());
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		TemplateSelectionService selection = new TemplateSelectionService(repository, runtime, new ResponderConfig());

		assertTrue(selection.connect("mc.vanilla-box.ru:25565").success());
		assertEquals("mc.vanilla-box.ru:25565", selection.currentAddress());
		assertEquals("vanilla-box", runtime.activeSnapshot().orElseThrow().id());

		selection.disconnect();
		assertNull(selection.currentAddress());
		assertTrue(runtime.activeSnapshot().isEmpty());
	}
}
