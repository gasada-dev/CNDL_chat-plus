package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
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
	void structurallyNullRuntimeFieldsAreSanitizedWithoutChangingAutomationCollections() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplate template = ServerTemplate.empty("safe", "Safe");
		template.globalPrefix = null;
		template.rules = null;
		template.periodicMessages = null;
		template.commands = null;
		template.parsers = null;
		assertTrue(repository.saveTemplate(template).success());

		ServerTemplate loaded = repository.loadTemplate("safe").value();
		assertEquals("", loaded.globalPrefix);
		assertNull(loaded.rules);
		assertNull(loaded.periodicMessages);
		assertEquals("", loaded.commands.pay);
		assertEquals("", loaded.parsers.lastSeenPattern);
	}

	@Test
	void automationBridgeRoundTripsWithoutNormalization() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplate template = ServerTemplate.empty("bridge", "Bridge");
		template.responderEnabled = false;
		template.rules = new ArrayList<>();
		ReplyRule rule = new ReplyRule(null, null, null);
		rule.enabled = false;
		template.rules.add(rule);
		template.rules.add(null);
		template.rules.add(new ReplyRule("last", "response", ChatChannel.PRIVATE));
		template.clanReplyPrefix = null;
		template.privateReplyCommand = null;
		template.periodicMessages = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			template.periodicMessages.add(new PeriodicMessageConfig(index % 2 == 0,
					index == 0 ? null : "message-" + index, index - 3));
		}
		template.periodicMessages.add(2, null);

		assertTrue(repository.saveTemplate(template).success());
		ServerTemplate loaded = repository.loadTemplate("bridge").value();

		assertFalse(loaded.responderEnabled);
		assertEquals(3, loaded.rules.size());
		assertFalse(loaded.rules.getFirst().enabled);
		assertNull(loaded.rules.getFirst().trigger);
		assertNull(loaded.rules.getFirst().response);
		assertNull(loaded.rules.getFirst().channel);
		assertNull(loaded.rules.get(1));
		assertEquals("last", loaded.rules.get(2).trigger);
		assertNull(loaded.clanReplyPrefix);
		assertNull(loaded.privateReplyCommand);
		assertEquals(6, loaded.periodicMessages.size());
		assertNull(loaded.periodicMessages.getFirst().message);
		assertEquals(-3, loaded.periodicMessages.getFirst().intervalMinutes);
		assertNull(loaded.periodicMessages.get(2));
		assertEquals(1, loaded.periodicMessages.get(5).intervalMinutes);
	}

	@Test
	void deepCopyPreservesNullableAutomationCollectionsAndElements() {
		ServerTemplate nullable = ServerTemplate.empty("source", "Source");
		nullable.rules = null;
		nullable.periodicMessages = null;
		nullable.clanReplyPrefix = null;
		nullable.privateReplyCommand = null;
		ServerTemplate nullableCopy = nullable.deepCopy("copy", "Copy");
		assertNull(nullableCopy.rules);
		assertNull(nullableCopy.periodicMessages);
		assertNull(nullableCopy.clanReplyPrefix);
		assertNull(nullableCopy.privateReplyCommand);

		ServerTemplate source = ServerTemplate.empty("source", "Source");
		ReplyRule rule = new ReplyRule(null, null, null);
		source.rules = new ArrayList<>();
		source.rules.add(null);
		source.rules.add(rule);
		source.periodicMessages = new ArrayList<>();
		source.periodicMessages.add(null);
		source.periodicMessages.add(new PeriodicMessageConfig(true, null, -9));
		ServerTemplate copy = source.deepCopy("copy", "Copy");
		assertNotSame(source.rules, copy.rules);
		assertNull(copy.rules.getFirst());
		assertNotSame(rule, copy.rules.get(1));
		assertNull(copy.rules.get(1).trigger);
		assertNull(copy.rules.get(1).response);
		assertNull(copy.rules.get(1).channel);
		assertNotSame(source.periodicMessages, copy.periodicMessages);
		assertNull(copy.periodicMessages.getFirst());
		assertNull(copy.periodicMessages.get(1).message);
		assertEquals(-9, copy.periodicMessages.get(1).intervalMinutes);
	}

	@Test
	void missingTemplateNameFailsInsteadOfPublishingCorruptData() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplate template = ServerTemplate.empty("bad-name", null);
		assertFalse(repository.saveTemplate(template).success());
	}

	@Test
	void managerCreatesAndCopiesIndependentTemplates() {
		ServerTemplateRepository repository = new ServerTemplateRepository(directory);
		ServerTemplateManager manager = new ServerTemplateManager(repository);
		ServerTemplate source = manager.createEmpty("source", "Source").value();
		source.friends.add("Alice");
		source.mutedWords.add("реклама");
		source.rules.add(new ReplyRule("one", "reply", ChatChannel.LOCAL));
		source.periodicMessages.add(new PeriodicMessageConfig(true, "legacy", 7));
		source.commands = ServerCommandSettings.vanillaBoxDefaults();
		assertTrue(repository.saveTemplate(source).success());

		ServerTemplate copy = manager.copy("source", "copy", "Copy").value();
		assertNotSame(source.friends, copy.friends);
		assertNotSame(source.rules, copy.rules);
		assertNotSame(source.periodicMessages, copy.periodicMessages);
		assertNotSame(source.commands, copy.commands);
		copy.friends.add("Bob");
		copy.rules.getFirst().trigger = "changed";
		copy.periodicMessages.getFirst().message = "changed";
		copy.commands.call = "different {player}";

		ServerTemplate reloadedSource = repository.loadTemplate("source").value();
		assertEquals(List.of("Alice"), reloadedSource.friends);
		assertEquals("one", reloadedSource.rules.getFirst().trigger);
		assertEquals("legacy", reloadedSource.periodicMessages.getFirst().message);
		assertEquals("call {player}", reloadedSource.commands.call);
	}
}
