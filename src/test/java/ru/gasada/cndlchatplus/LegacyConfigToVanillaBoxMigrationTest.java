package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

	@Test
	void fullLegacyCopyPrefersPeriodicListAndDoesNotMutateSource() {
		ResponderConfig legacy = new ResponderConfig();
		legacy.enabled = false;
		ReplyRule rule = new ReplyRule(null, "reply", null);
		rule.enabled = false;
		legacy.rules = new ArrayList<>();
		legacy.rules.add(rule);
		legacy.rules.add(null);
		legacy.periodicMessages = new ArrayList<>();
		legacy.periodicMessages.add(new PeriodicMessageConfig(true, null, 0));
		legacy.periodicMessages.add(null);
		legacy.periodicMessages.add(new PeriodicMessageConfig(false, "second", -7));
		legacy.periodicEnabled = true;
		legacy.periodicMessage = "singleton";
		legacy.periodicIntervalMinutes = 99;
		Gson gson = new GsonBuilder().serializeNulls().create();
		String before = gson.toJson(legacy);

		ServerTemplate template = ServerTemplate.empty("target", "Target");
		LegacyConfigToVanillaBoxMigration.applyLegacyFields(template, legacy);

		assertEquals(before, gson.toJson(legacy));
		assertFalse(template.responderEnabled);
		assertEquals(2, template.rules.size());
		assertNull(template.rules.getFirst().trigger);
		assertNull(template.rules.getFirst().channel);
		assertNull(template.rules.get(1));
		assertEquals(3, template.periodicMessages.size());
		assertNull(template.periodicMessages.getFirst().message);
		assertEquals(0, template.periodicMessages.getFirst().intervalMinutes);
		assertNull(template.periodicMessages.get(1));
		assertEquals("second", template.periodicMessages.get(2).message);
	}

	@Test
	void fullLegacyCopyDerivesPeriodicEntryFromSingletonWhenListIsEmpty() {
		ResponderConfig legacy = new ResponderConfig();
		legacy.periodicMessages = null;
		legacy.periodicEnabled = false;
		legacy.periodicMessage = null;
		legacy.periodicIntervalMinutes = null;

		ServerTemplate template = ServerTemplate.empty("target", "Target");
		LegacyConfigToVanillaBoxMigration.applyLegacyFields(template, legacy);

		assertNull(legacy.periodicMessages);
		assertFalse(legacy.periodicEnabled);
		assertNull(legacy.periodicMessage);
		assertNull(legacy.periodicIntervalMinutes);
		assertEquals(1, template.periodicMessages.size());
		assertFalse(template.periodicMessages.getFirst().enabled);
		assertEquals("", template.periodicMessages.getFirst().message);
		assertEquals(5, template.periodicMessages.getFirst().intervalMinutes);
	}

	@Test
	void visibleFieldCopyLeavesEveryInertAutomationFieldUnchanged() {
		ServerTemplate template = ServerTemplate.empty("target", "Target");
		template.responderEnabled = false;
		ReplyRule rule = new ReplyRule(null, "target reply", null);
		template.rules.add(rule);
		template.rules.add(null);
		for (int index = 0; index < 5; index++) {
			template.periodicMessages.add(new PeriodicMessageConfig(index % 2 == 0,
					"target-" + index, index - 2));
		}
		template.periodicMessages.add(null);
		template.clanReplyPrefix = "/target-clan";
		template.privateReplyCommand = "/target-private";
		template.friendSoundEnabled = false;
		List<ReplyRule> rules = template.rules;
		List<PeriodicMessageConfig> periodic = template.periodicMessages;

		ResponderConfig visible = new ResponderConfig();
		visible.enabled = true;
		visible.rules.add(new ReplyRule("source", "source", ChatChannel.AUTO));
		visible.periodicMessages.add(new PeriodicMessageConfig(true, "source", 100));
		visible.clanReplyPrefix = "/source-clan";
		visible.privateReplyCommand = "/source-private";
		visible.globalPrefix = "$";
		visible.globalMarkers = "[world]";
		visible.clanMarkers = "[clan]";
		visible.privateMarkers = "[private]";
		visible.mutedWords = new ArrayList<>(List.of("muted"));
		visible.discordChatEnabled = false;
		visible.discordMutedPlayers = new ArrayList<>(List.of("DiscordUser"));
		visible.friends = new ArrayList<>(List.of("Friend"));
		visible.friendLastSeen.put("Friend", "today");
		visible.friendHudEnabled = false;
		visible.teleportAutoAcceptMode = TeleportAutoAcceptMode.SELECTED_FRIENDS;
		visible.teleportAutoAcceptFriends.add("Friend");

		LegacyConfigToVanillaBoxMigration.applyVisibleFields(template, visible);

		assertFalse(template.responderEnabled);
		assertSame(rules, template.rules);
		assertSame(rule, template.rules.getFirst());
		assertNull(template.rules.getFirst().trigger);
		assertNull(template.rules.get(1));
		assertSame(periodic, template.periodicMessages);
		assertEquals(6, template.periodicMessages.size());
		assertEquals(List.of(-2, -1, 0, 1, 2), template.periodicMessages.subList(0, 5).stream()
				.map(message -> message.intervalMinutes).toList());
		assertNull(template.periodicMessages.get(5));
		assertEquals("/target-clan", template.clanReplyPrefix);
		assertEquals("/target-private", template.privateReplyCommand);
		assertFalse(template.friendSoundEnabled);
		assertEquals("$", template.globalPrefix);
		assertEquals("[world]", template.globalMarkers);
		assertEquals("[clan]", template.clanMarkers);
		assertEquals("[private]", template.privateMarkers);
		assertEquals(List.of("muted"), template.mutedWords);
		assertTrue(template.discordChatEnabled);
		assertEquals(List.of("DiscordUser"), template.discordMutedPlayers);
		assertEquals(List.of("Friend"), template.friends);
		assertEquals("today", template.friendLastSeen.get("Friend"));
		assertTrue(template.friendHudEnabled);
		assertEquals(TeleportAutoAcceptMode.SELECTED_FRIENDS, template.teleportAutoAcceptMode);
		assertEquals(List.of("Friend"), template.teleportAutoAcceptFriends);
	}

	@Test
	void populateLegacyViewPreservesNullableAutomationWithoutMutatingTemplate() {
		ServerTemplate template = ServerTemplate.empty("target", "Target");
		template.responderEnabled = false;
		template.discordChatEnabled = false;
		template.friendHudEnabled = false;
		template.rules = null;
		template.periodicMessages = null;
		template.clanReplyPrefix = null;
		template.privateReplyCommand = null;
		Gson gson = new GsonBuilder().serializeNulls().create();
		String before = gson.toJson(template);

		ResponderConfig view = new ResponderConfig();
		LegacyConfigToVanillaBoxMigration.populateLegacyView(view, template);

		assertEquals(before, gson.toJson(template));
		assertFalse(view.enabled);
		assertNull(view.rules);
		assertNull(view.periodicMessages);
		assertNull(view.clanReplyPrefix);
		assertNull(view.privateReplyCommand);
		assertTrue(view.discordChatEnabled);
		assertTrue(view.friendHudEnabled);
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
