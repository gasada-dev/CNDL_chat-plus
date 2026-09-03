package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

final class ResponderConfigTest {
	@Test
	void defaultsPreserveLegacyCompatibilityConfiguration() {
		ResponderConfig config = ResponderConfig.defaults();

		assertTrue(config.enabled);
		assertTrue(config.discordChatEnabled);
		assertTrue(config.friendHudEnabled);
		assertTrue(config.friendSoundEnabled);
		assertTrue(config.teleportRequestSoundEnabled);
		assertTrue(config.chatDuplicateCollapseEnabled);
		assertEquals(TeleportAutoAcceptMode.OFF, config.teleportAutoAcceptMode);
		assertTrue(config.teleportAutoAcceptFriends.isEmpty());
		assertEquals("!", config.globalPrefix);
		assertEquals("/.", config.clanReplyPrefix);
		assertEquals("/r", config.privateReplyCommand);
		assertEquals(1, config.rules.size());
		assertEquals("Всем привет", config.rules.getFirst().trigger);
		assertEquals("привет", config.rules.getFirst().response);
		assertEquals(ChatChannel.AUTO, config.rules.getFirst().channel);
		assertEquals(1, config.periodicMessages.size());
		assertFalse(config.periodicMessages.getFirst().enabled);
		assertEquals("", config.periodicMessages.getFirst().message);
		assertEquals(5, config.periodicMessages.getFirst().intervalMinutes);
	}

	@Test
	void sanitizeRestoresNullFieldsAndCollections() {
		ResponderConfig config = new ResponderConfig();
		config.discordChatEnabled = null;
		config.discordMutedPlayers = null;
		config.mutedWords = null;
		config.friends = null;
		config.friendLastSeen = null;
		config.friendHudEnabled = null;
		config.friendSoundEnabled = null;
		config.teleportRequestSoundEnabled = null;
		config.teleportAutoAcceptMode = null;
		config.teleportAutoAcceptFriends = null;
		config.periodicMessages = null;
		config.rules = null;
		config.globalPrefix = null;
		config.clanReplyPrefix = null;
		config.privateReplyCommand = null;
		config.globalMarkers = null;
		config.clanMarkers = null;
		config.privateMarkers = null;

		config.sanitize();

		assertTrue(config.discordChatEnabled);
		assertTrue(config.friendHudEnabled);
		assertTrue(config.friendSoundEnabled);
		assertTrue(config.teleportRequestSoundEnabled);
		assertEquals(TeleportAutoAcceptMode.OFF, config.teleportAutoAcceptMode);
		assertNotNull(config.teleportAutoAcceptFriends);
		assertNotNull(config.discordMutedPlayers);
		assertNotNull(config.mutedWords);
		assertNotNull(config.friends);
		assertNotNull(config.friendLastSeen);
		assertNull(config.rules);
		assertNull(config.periodicMessages);
		assertEquals("!", config.globalPrefix);
		assertNull(config.clanReplyPrefix);
		assertNull(config.privateReplyCommand);
		assertEquals("(!)", config.globalMarkers);
		assertEquals("", config.clanMarkers);
		assertEquals("", config.privateMarkers);
	}

	@Test
	void sanitizeRestoresChatHistoryDefaultsAndClampsLimit() {
		ResponderConfig config = new ResponderConfig();
		config.chatHistoryEnabled = null;
		config.chatHistoryPersist = null;
		config.chatHistoryLimit = null;

		config.sanitize();

		assertTrue(config.chatHistoryEnabled);
		assertTrue(config.chatHistoryPersist);
		assertEquals(ResponderConfig.DEFAULT_CHAT_HISTORY_LIMIT, config.chatHistoryLimit);

		config.chatHistoryLimit = 1;
		config.sanitize();
		assertEquals(ResponderConfig.MIN_CHAT_HISTORY_LIMIT, config.chatHistoryLimit);

		config.chatHistoryLimit = 999_999;
		config.sanitize();
		assertEquals(ResponderConfig.MAX_CHAT_HISTORY_LIMIT, config.chatHistoryLimit);
	}

	@Test
	void sanitizeRestoresChatUiDefaults() {
		ResponderConfig config = new ResponderConfig();
		config.chatTabsEnabled = null;
		config.chatTimestampsEnabled = null;
		config.chatSearchEnabled = null;
		config.chatContextMenuEnabled = null;
		config.chatDuplicateCollapseEnabled = null;

		config.sanitize();

		assertTrue(config.chatTabsEnabled);
		assertTrue(config.chatTimestampsEnabled);
		assertTrue(config.chatSearchEnabled);
		assertTrue(config.chatContextMenuEnabled);
		assertTrue(config.chatDuplicateCollapseEnabled);
	}

	@Test
	void sanitizeRemovesBlankEntriesAndDeduplicatesIgnoringCase() {
		ResponderConfig config = new ResponderConfig();
		config.discordMutedPlayers = mutableList(" User ", "user", "", null, "Other");
		config.mutedWords = mutableList(" Реклама ", "реклама", "   ", null, "Другое");
		config.friends = mutableList(" Steve ", "steve", "", null, "Alex");
		config.teleportAutoAcceptFriends = mutableList(" steve ", "STEVE", "Missing", "", null);

		config.sanitize();

		assertEquals(List.of("User", "Other"), config.discordMutedPlayers);
		assertEquals(List.of("Реклама", "Другое"), config.mutedWords);
		assertEquals(List.of("Steve", "Alex"), config.friends);
		assertEquals(List.of("steve"), config.teleportAutoAcceptFriends);
	}

	@Test
	void sanitizeRemovesIncompleteLastSeenEntries() {
		ResponderConfig config = new ResponderConfig();
		config.friendLastSeen = new LinkedHashMap<>();
		config.friendLastSeen.put("Steve", "вчера");
		config.friendLastSeen.put("", "сегодня");
		config.friendLastSeen.put(null, "сегодня");
		config.friendLastSeen.put("Alex", " ");
		config.friendLastSeen.put("Bob", null);

		config.sanitize();

		assertEquals(new LinkedHashMap<>(java.util.Map.of("Steve", "вчера")), config.friendLastSeen);
	}

	@Test
	void sanitizePreservesUnusualInertAutomationByteForByte() {
		ResponderConfig config = new ResponderConfig();
		config.enabled = false;
		config.rules.clear();
		ReplyRule first = new ReplyRule(null, "ТИХ ТИХ", null);
		first.enabled = false;
		config.rules.add(first);
		config.rules.add(new ReplyRule("Амадо где Гасада", null, ChatChannel.LOCAL));
		config.rules.add(new ReplyRule("Гасада где Амадо", "тих тих", ChatChannel.GLOBAL));
		config.periodicMessages.clear();
		config.periodicMessages.add(new PeriodicMessageConfig(false, null, 0));
		config.periodicMessages.add(new PeriodicMessageConfig(true, "second", -5));
		config.periodicMessages.add(new PeriodicMessageConfig(false, "third", 3));
		config.periodicMessages.add(new PeriodicMessageConfig(true, "fourth", Integer.MAX_VALUE));
		config.periodicMessages.add(new PeriodicMessageConfig(false, "fifth", Integer.MIN_VALUE));
		config.periodicEnabled = true;
		config.periodicMessage = null;
		config.periodicIntervalMinutes = -17;
		config.clanReplyPrefix = null;
		config.privateReplyCommand = "/odd reply";
		List<ReplyRule> rules = config.rules;
		List<PeriodicMessageConfig> periodic = config.periodicMessages;
		Gson gson = new GsonBuilder().serializeNulls().create();
		String before = gson.toJson(config);

		config.sanitize();

		assertEquals(before, gson.toJson(config));
		assertSame(rules, config.rules);
		assertSame(periodic, config.periodicMessages);
		assertEquals(3, config.rules.size());
		assertNull(config.rules.getFirst().trigger);
		assertNull(config.rules.getFirst().channel);
		assertEquals("Амадо где Гасада", config.rules.get(1).trigger);
		assertEquals(5, config.periodicMessages.size());
		assertEquals(List.of(0, -5, 3, Integer.MAX_VALUE, Integer.MIN_VALUE),
				config.periodicMessages.stream().map(message -> message.intervalMinutes).toList());
		assertNull(config.periodicMessages.getFirst().message);
		assertTrue(config.periodicEnabled);
		assertNull(config.periodicMessage);
		assertEquals(-17, config.periodicIntervalMinutes);
		assertNull(config.clanReplyPrefix);
		assertEquals("/odd reply", config.privateReplyCommand);
	}

	@Test
	void sanitizeRestoresMandatoryVisibleGlobalMarkerWithoutTouchingNullAutomationCollections() {
		ResponderConfig config = new ResponderConfig();
		config.globalMarkers = "[custom]";
		config.rules = null;
		config.periodicMessages = null;

		config.sanitize();

		assertEquals("(!),[custom]", config.globalMarkers);
		assertNull(config.rules);
		assertNull(config.periodicMessages);
	}

	@Test
	void globalSettingsCopyDoesNotReplaceServerViewOrAutomationBridge() {
		ResponderConfig persisted = new ResponderConfig();
		persisted.friends.add("ServerFriend");
		persisted.mutedWords.add("server-word");
		persisted.rules = List.of(new ReplyRule("trigger", "reply", ChatChannel.AUTO));
		ResponderConfig source = new ResponderConfig();
		source.discordChatEnabled = false;
		source.friendHudEnabled = false;
		source.friendSoundEnabled = false;
		source.teleportRequestSoundEnabled = false;
		source.chatTabsEnabled = false;
		source.chatDuplicateCollapseEnabled = false;

		persisted.applyGlobalSettingsFrom(source);

		assertFalse(persisted.discordChatEnabled);
		assertFalse(persisted.friendHudEnabled);
		assertFalse(persisted.friendSoundEnabled);
		assertFalse(persisted.teleportRequestSoundEnabled);
		assertFalse(persisted.chatTabsEnabled);
		assertFalse(persisted.chatDuplicateCollapseEnabled);
		assertEquals(List.of("ServerFriend"), persisted.friends);
		assertEquals(List.of("server-word"), persisted.mutedWords);
		assertEquals("trigger", persisted.rules.getFirst().trigger);
		assertTrue(persisted.chatSearchEnabled);
	}

	@SafeVarargs
	private static <T> ArrayList<T> mutableList(T... values) {
		return new ArrayList<>(java.util.Arrays.asList(values));
	}
}
