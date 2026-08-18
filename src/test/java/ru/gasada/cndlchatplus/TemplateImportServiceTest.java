package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TemplateImportServiceTest {
	@TempDir Path directory;
	private ServerTemplateRepository repository;
	private TemplateImportService service;

	@BeforeEach
	void setUp() {
		repository = new ServerTemplateRepository(directory);
		service = new TemplateImportService(repository);
	}

	@Test
	void previewDoesNotMutateEitherTemplateAndApplyRequiresConfirmation() {
		ServerTemplate source = template("source");
		source.friends.addAll(List.of("Alice", "Bob"));
		source.mutedWords.add("SourceWord");
		ServerTemplate target = template("target");
		target.friends.add("alice");
		target.mutedWords.add("TargetWord");
		save(source, target);
		TemplateImportOptions options = new TemplateImportOptions()
				.select(TemplateImportOptions.Category.FRIENDS, true)
				.select(TemplateImportOptions.Category.MUTED_WORDS, true);

		TemplateImportPreview preview = service.preview("source", "target", options).value();
		assertTrue(preview.valid());
		assertEquals(List.of("Alice", "Bob"), repository.loadTemplate("source").value().friends);
		assertEquals(List.of("alice"), repository.loadTemplate("target").value().friends);
		assertFalse(service.apply(preview, false).success());
		assertEquals(List.of("alice"), repository.loadTemplate("target").value().friends);

		assertTrue(service.apply(preview, true).success());
		ServerTemplate imported = repository.loadTemplate("target").value();
		assertEquals(List.of("alice", "Bob"), imported.friends);
		assertEquals(List.of("TargetWord", "SourceWord"), imported.mutedWords);
	}

	@Test
	void listModesReplaceMergeAndSkipAreIndependentPerCategory() {
		ServerTemplate source = template("source");
		source.friends.add("SourceFriend");
		source.mutedWords.add("SourceWord");
		source.discordMutedPlayers.add("SourceDiscord");
		ServerTemplate target = template("target");
		target.friends.add("TargetFriend");
		target.mutedWords.add("TargetWord");
		target.discordMutedPlayers.add("TargetDiscord");
		save(source, target);
		TemplateImportOptions options = new TemplateImportOptions();
		options.select(TemplateImportOptions.Category.FRIENDS, true)
				.listMode(TemplateImportOptions.Category.FRIENDS, TemplateImportOptions.ListMode.REPLACE);
		options.select(TemplateImportOptions.Category.MUTED_WORDS, true)
				.listMode(TemplateImportOptions.Category.MUTED_WORDS, TemplateImportOptions.ListMode.MERGE);
		options.select(TemplateImportOptions.Category.MUTED_DISCORD_USERS, true)
				.listMode(TemplateImportOptions.Category.MUTED_DISCORD_USERS, TemplateImportOptions.ListMode.SKIP);

		TemplateImportPreview preview = service.preview("source", "target", options).value();
		assertTrue(service.apply(preview, true).success());
		ServerTemplate imported = repository.loadTemplate("target").value();
		assertEquals(List.of("SourceFriend"), imported.friends);
		assertEquals(List.of("TargetWord", "SourceWord"), imported.mutedWords);
		assertEquals(List.of("TargetDiscord"), imported.discordMutedPlayers);
	}

	@Test
	void importingOtherCategoriesPreservesInertAutomationBridge() {
		ServerTemplate source = template("source");
		source.rules.add(new ReplyRule("source-rule", "source-reply", ChatChannel.AUTO));
		source.periodicMessages.add(new PeriodicMessageConfig(true, "source-periodic", 1));
		source.globalPrefix = "$";
		source.clanReplyPrefix = "/source-clan";
		source.privateReplyCommand = "/source-reply";
		source.globalMarkers = "[source]";
		source.friendLastSeen.put("Alice", "старое");
		source.friendLastSeen.put("Bob", "вчера");
		ServerTemplate target = template("target");
		target.responderEnabled = false;
		target.rules = new ArrayList<>();
		ReplyRule targetRule = new ReplyRule(null, null, null);
		targetRule.enabled = false;
		target.rules.add(targetRule);
		target.rules.add(null);
		target.rules.add(new ReplyRule("last-rule", "last-reply", ChatChannel.PRIVATE));
		target.periodicMessages = new ArrayList<>();
		for (int index = 0; index < 5; index++) {
			target.periodicMessages.add(new PeriodicMessageConfig(index % 2 == 0,
					index == 0 ? null : "target-" + index, index - 3));
		}
		target.periodicMessages.add(2, null);
		target.clanReplyPrefix = null;
		target.privateReplyCommand = null;
		target.friendLastSeen.put("alice", "новое");
		save(source, target);
		TemplateImportOptions options = new TemplateImportOptions()
				.select(TemplateImportOptions.Category.CHANNELS_AND_MARKERS, true)
				.select(TemplateImportOptions.Category.LAST_SEEN, true);

		TemplateImportPreview preview = service.preview("source", "target", options).value();
		assertAutomationBridgePreserved(preview.proposedTargetCopy());
		ServerTemplate imported = service.apply(preview, true).value();
		assertAutomationBridgePreserved(imported);
		assertAutomationBridgePreserved(repository.loadTemplate("target").value());
		assertEquals("$", imported.globalPrefix);
		assertEquals("[source]", imported.globalMarkers);
		assertEquals("новое", imported.friendLastSeen.get("alice"));
		assertEquals("вчера", imported.friendLastSeen.get("Bob"));

		options.overwriteExistingLastSeen(true);
		ServerTemplate overwritten = service.apply(service.preview("source", "target", options).value(), true).value();
		assertEquals("старое", overwritten.friendLastSeen.get("alice"));
		assertEquals("source-rule", repository.loadTemplate("source").value().rules.getFirst().trigger);
	}

	@Test
	void invalidImportedCommandPreventsAnyTargetWrite() {
		ServerTemplate source = template("source");
		source.commands = ServerCommandSettings.vanillaBoxDefaults();
		source.commands.pay = "pay {player}";
		ServerTemplate target = template("target");
		target.commands = ServerCommandSettings.vanillaBoxDefaults();
		save(source, target);
		TemplateImportOptions options = new TemplateImportOptions()
				.select(TemplateImportOptions.Category.COMMANDS, true);

		TemplateImportPreview preview = service.preview("source", "target", options).value();
		assertFalse(preview.valid());
		assertFalse(service.apply(preview, true).success());
		assertEquals("pay {player} {amount}", repository.loadTemplate("target").value().commands.pay);
	}

	@Test
	void importRemovesMarriageSettingsOutsideVanillaGame() {
		ServerTemplate source = template("vanilla-game");
		source.commands.marriageList = "marry list {page}";
		ParserSettings.applyVanillaGameMarriageDefaults(source.parsers);
		source.playerInfo.marriageLookupConfigured = true;
		ServerTemplate target = template("vanilla-box");
		save(source, target);
		TemplateImportOptions options = new TemplateImportOptions()
				.select(TemplateImportOptions.Category.COMMANDS, true)
				.select(TemplateImportOptions.Category.PARSER_PATTERNS, true)
				.select(TemplateImportOptions.Category.PLAYER_INFO, true);

		ServerTemplate imported = service.apply(
				service.preview("vanilla-game", "vanilla-box", options).value(), true).value();
		assertTrue(imported.commands.marriageList.isBlank());
		assertTrue(imported.parsers.marriageEntryPattern.isBlank());
		assertFalse(imported.playerInfo.marriageLookupConfigured);
	}

	private void save(ServerTemplate... templates) {
		for (ServerTemplate template : templates) assertTrue(repository.saveTemplate(template).success());
	}

	private static ServerTemplate template(String id) {
		ServerTemplate template = ServerTemplate.empty(id, id);
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		return template;
	}

	private static void assertAutomationBridgePreserved(ServerTemplate template) {
		assertFalse(template.responderEnabled);
		assertEquals(3, template.rules.size());
		assertFalse(template.rules.getFirst().enabled);
		assertNull(template.rules.getFirst().trigger);
		assertNull(template.rules.getFirst().response);
		assertNull(template.rules.getFirst().channel);
		assertNull(template.rules.get(1));
		assertEquals("last-rule", template.rules.get(2).trigger);
		assertNull(template.clanReplyPrefix);
		assertNull(template.privateReplyCommand);
		assertEquals(6, template.periodicMessages.size());
		assertNull(template.periodicMessages.getFirst().message);
		assertEquals(-3, template.periodicMessages.getFirst().intervalMinutes);
		assertNull(template.periodicMessages.get(2));
		assertEquals(1, template.periodicMessages.get(5).intervalMinutes);
	}
}
