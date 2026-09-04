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
		assertTrue(config.chatAlertsEnabled);
		assertTrue(config.chatAlertRules.isEmpty());
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
		config.chatAlertsEnabled = null;
		config.chatAlertRules = null;

		config.sanitize();

		assertTrue(config.chatTabsEnabled);
		assertTrue(config.chatTimestampsEnabled);
		assertTrue(config.chatSearchEnabled);
		assertTrue(config.chatContextMenuEnabled);
		assertTrue(config.chatDuplicateCollapseEnabled);
		assertTrue(config.chatAlertsEnabled);
		assertTrue(config.chatAlertRules.isEmpty());
	}

	@Test
	void sanitizeRepairsAndBoundsAlertRules() {
		ResponderConfig config = new ResponderConfig();
		config.chatAlertRules = new ArrayList<>();
		config.chatAlertRules.add(null);
		config.chatAlertRules.add(new ChatAlertRule("same", "  ", true, null,
				" value ", null, true, false, -5));
		config.chatAlertRules.add(new ChatAlertRule("same", "n".repeat(80), true,
				ChatAlertMatchType.TEXT, "x".repeat(300), ChatAlertChannel.LOCAL, false, true, 9_999));
		config.chatAlertRules.add(new ChatAlertRule("blank", "blank", true,
				ChatAlertMatchType.TEXT, "   ", ChatAlertChannel.ANY, true, true, 1));
		for (int index = 0; index < 110; index++) {
			config.chatAlertRules.add(new ChatAlertRule("id-" + index, "rule", true,
					ChatAlertMatchType.TEXT, "pattern", ChatAlertChannel.ANY, true, true, 0));
		}

		config.sanitize();

		assertEquals(ResponderConfig.MAX_CHAT_ALERT_RULES, config.chatAlertRules.size());
		assertEquals("Название", config.chatAlertRules.getFirst().name);
		assertEquals(ChatAlertMatchType.TEXT, config.chatAlertRules.getFirst().matchType);
		assertEquals(ChatAlertChannel.ANY, config.chatAlertRules.getFirst().channel);
		assertEquals(0, config.chatAlertRules.getFirst().cooldownSeconds);
		assertNotNull(config.chatAlertRules.get(1).id);
		assertFalse(config.chatAlertRules.getFirst().id.equals(config.chatAlertRules.get(1).id));
		assertEquals(ResponderConfig.MAX_CHAT_ALERT_NAME_LENGTH, config.chatAlertRules.get(1).name.length());
		assertEquals(ResponderConfig.MAX_CHAT_ALERT_PATTERN_LENGTH,
				config.chatAlertRules.get(1).pattern.length());
		assertEquals(3600, config.chatAlertRules.get(1).cooldownSeconds);
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
		source.chatAlertsEnabled = false;
		source.chatAlertRules.add(new ChatAlertRule("alert", "Alert", true, ChatAlertMatchType.TEXT,
				"word", ChatAlertChannel.ANY, true, false, 3));

		persisted.applyGlobalSettingsFrom(source);

		assertFalse(persisted.discordChatEnabled);
		assertFalse(persisted.friendHudEnabled);
		assertFalse(persisted.friendSoundEnabled);
		assertFalse(persisted.teleportRequestSoundEnabled);
		assertFalse(persisted.chatTabsEnabled);
		assertFalse(persisted.chatDuplicateCollapseEnabled);
		assertFalse(persisted.chatAlertsEnabled);
		assertEquals("alert", persisted.chatAlertRules.getFirst().id);
		assertEquals("trigger", persisted.rules.getFirst().trigger);
		assertEquals(List.of("ServerFriend"), persisted.friends);
		assertEquals(List.of("server-word"), persisted.mutedWords);
		assertTrue(persisted.chatSearchEnabled);
	}

	@Test
	void tolerantJsonSkipsMalformedAlertsWithoutLosingAutomationBridge() {
		Gson gson = new GsonBuilder().serializeNulls().create();
		ResponderConfig config = ResponderConfigJson.read(gson, """
				{
				  "enabled": false,
				  "rules": [{"trigger": null, "response": "reply", "channel": null}],
				  "periodicMessages": null,
				  "chatAlertsEnabled": "wrong",
				  "chatAlertRules": [
				    {"id":"valid","name":"Name","enabled":true,"matchType":"TEXT","pattern":"word","channel":"ANY","hudEnabled":true,"soundEnabled":false,"cooldownSeconds":3},
				    {"id":"broken","pattern":{"not":"text"}},
				    null
				  ]
				}
				""");
		config.sanitize();

		assertFalse(config.enabled);
		assertEquals(1, config.rules.size());
		assertNull(config.rules.getFirst().trigger);
		assertNull(config.rules.getFirst().channel);
		assertNull(config.periodicMessages);
		assertTrue(config.chatAlertsEnabled);
		assertEquals(List.of("valid"), config.chatAlertRules.stream().map(rule -> rule.id).toList());
	}

	@Test
	void alertRulesRoundTripExactly() {
		Gson gson = new GsonBuilder().serializeNulls().create();
		ResponderConfig source = new ResponderConfig();
		source.chatAlertsEnabled = false;
		source.chatAlertRules.add(new ChatAlertRule("stable", "Regex", false, ChatAlertMatchType.REGEX,
				"^тест$", ChatAlertChannel.CLAN, false, true, 42));

		ResponderConfig restored = ResponderConfigJson.read(gson, gson.toJson(source));
		restored.sanitize();

		assertFalse(restored.chatAlertsEnabled);
		assertEquals(gson.toJson(source.chatAlertRules), gson.toJson(restored.chatAlertRules));
	}

	@SafeVarargs
	private static <T> ArrayList<T> mutableList(T... values) {
		return new ArrayList<>(java.util.Arrays.asList(values));
	}
}
