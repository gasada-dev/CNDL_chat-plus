package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ServerTemplateRepositoryTest {
	@TempDir
	Path directory;

	@Test
	void savesAndLoadsRootAndTemplateJson() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		RootConfig root = new RootConfig();
		root.defaultTemplateId = "alpha";
		root.templates.add(new ServerTemplateInfo("alpha", "Alpha"));
		root.serverBindings.put("example.org:25565", "alpha");
		assertTrue(repository.saveRoot(root).success());

		ServerTemplate template = ServerTemplate.empty("alpha", "Alpha");
		template.rules.add(new ReplyRule("привет*", "ответ", ChatChannel.AUTO));
		template.friends.add("Friend_1");
		template.periodicMessages.add(new PeriodicMessageConfig(true, "текст", 5));
		assertTrue(repository.saveTemplate(template).success());

		RootConfig loadedRoot = repository.loadRoot().value();
		ServerTemplate loadedTemplate = repository.loadTemplate("alpha").value();
		assertEquals("alpha", loadedRoot.defaultTemplateId);
		assertEquals("alpha", loadedRoot.serverBindings.get("example.org:25565"));
		assertEquals("привет*", loadedTemplate.rules.getFirst().trigger);
		assertEquals(List.of("Friend_1"), loadedTemplate.friends);
		assertEquals("текст", loadedTemplate.periodicMessages.getFirst().message);
	}

	@Test
	void rejectsUnsafeTemplateIds() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		assertFalse(repository.saveTemplate(ServerTemplate.empty("../escape", "bad")).success());
		assertFalse(repository.loadTemplate("../escape").success());
	}

	@Test
	void managerCreatesAndCopiesIndependentTemplates() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplateManager manager = new ServerTemplateManager(repository);
		ServerTemplate source = manager.createEmpty("source", "Source").value();
		source.friends.add("Alice");
		source.mutedWords.add("реклама");
		source.rules.add(new ReplyRule("one", "reply", ChatChannel.LOCAL));
		source.commands = ServerCommandSettings.vanillaBoxDefaults();
		assertTrue(repository.saveTemplate(source).success());

		ServerTemplate copy = manager.copy("source", "copy", "Copy").value();
		assertNotSame(source.friends, copy.friends);
		assertNotSame(source.rules, copy.rules);
		assertNotSame(source.commands, copy.commands);
		copy.friends.add("Bob");
		copy.rules.getFirst().trigger = "changed";
		copy.commands.call = "different {player}";

		ServerTemplate reloadedSource = repository.loadTemplate("source").value();
		assertEquals(List.of("Alice"), reloadedSource.friends);
		assertEquals("one", reloadedSource.rules.getFirst().trigger);
		assertEquals("call {player}", reloadedSource.commands.call);
	}
}
