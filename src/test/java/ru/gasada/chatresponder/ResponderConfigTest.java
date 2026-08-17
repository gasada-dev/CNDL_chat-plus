package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ResponderConfigTest {
	@Test
	void defaultsCreateCurrentOperationalConfiguration() {
		ResponderConfig config = ResponderConfig.defaults();

		assertTrue(config.enabled);
		assertTrue(config.discordChatEnabled);
		assertTrue(config.friendHudEnabled);
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
		assertNotNull(config.discordMutedPlayers);
		assertNotNull(config.mutedWords);
		assertNotNull(config.friends);
		assertNotNull(config.friendLastSeen);
		assertNotNull(config.rules);
		assertEquals(1, config.periodicMessages.size());
		assertEquals("!", config.globalPrefix);
		assertEquals("/.", config.clanReplyPrefix);
		assertEquals("/r", config.privateReplyCommand);
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

		config.sanitize();

		assertTrue(config.chatTabsEnabled);
		assertTrue(config.chatTimestampsEnabled);
		assertTrue(config.chatSearchEnabled);
		assertTrue(config.chatContextMenuEnabled);
	}

	@Test
	void sanitizeRemovesBlankEntriesAndDeduplicatesIgnoringCase() {
		ResponderConfig config = new ResponderConfig();
		config.discordMutedPlayers = mutableList(" User ", "user", "", null, "Other");
		config.mutedWords = mutableList(" Реклама ", "реклама", "   ", null, "Другое");
		config.friends = mutableList(" Steve ", "steve", "", null, "Alex");

		config.sanitize();

		assertEquals(List.of("User", "Other"), config.discordMutedPlayers);
		assertEquals(List.of("Реклама", "Другое"), config.mutedWords);
		assertEquals(List.of("Steve", "Alex"), config.friends);
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
	void sanitizeMigratesExactOldDefaultRules() {
		ResponderConfig config = new ResponderConfig();
		config.rules.add(new ReplyRule("Амадо где Гасада", "ТИХ ТИХ", ChatChannel.LOCAL));
		config.rules.add(new ReplyRule("Гасада где Амадо", "тих тих", ChatChannel.GLOBAL));

		config.sanitize();

		assertEquals(1, config.rules.size());
		ReplyRule migrated = config.rules.getFirst();
		assertEquals("Всем привет", migrated.trigger);
		assertEquals("привет", migrated.response);
		assertEquals(ChatChannel.AUTO, migrated.channel);
		assertTrue(migrated.enabled);
	}

	@Test
	void sanitizeMigratesLegacyPeriodicFieldsWhenNewListIsEmpty() {
		ResponderConfig config = new ResponderConfig();
		config.periodicMessages.clear();
		config.periodicEnabled = true;
		config.periodicMessage = "сообщение";
		config.periodicIntervalMinutes = 17;

		config.sanitize();

		assertEquals(1, config.periodicMessages.size());
		PeriodicMessageConfig migrated = config.periodicMessages.getFirst();
		assertTrue(migrated.enabled);
		assertEquals("сообщение", migrated.message);
		assertEquals(17, migrated.intervalMinutes);
		assertNull(config.periodicEnabled);
		assertNull(config.periodicMessage);
		assertNull(config.periodicIntervalMinutes);
	}

	@Test
	void existingPeriodicListTakesPriorityOverLegacyFields() {
		ResponderConfig config = new ResponderConfig();
		config.periodicMessages.add(new PeriodicMessageConfig(false, "новое", 9));
		config.periodicEnabled = true;
		config.periodicMessage = "старое";
		config.periodicIntervalMinutes = 2;

		config.sanitize();

		assertEquals(1, config.periodicMessages.size());
		assertEquals("новое", config.periodicMessages.getFirst().message);
		assertNull(config.periodicEnabled);
		assertNull(config.periodicMessage);
		assertNull(config.periodicIntervalMinutes);
	}

	@Test
	void sanitizeLimitsPeriodicMessagesToFirstThree() {
		ResponderConfig config = new ResponderConfig();
		for (int index = 1; index <= 5; index++) {
			config.periodicMessages.add(new PeriodicMessageConfig(true, "message-" + index, index));
		}

		config.sanitize();

		assertEquals(3, config.periodicMessages.size());
		assertEquals(List.of("message-1", "message-2", "message-3"),
				config.periodicMessages.stream().map(message -> message.message).toList());
	}

	@Test
	void sanitizeRepairsIntervalsBelowOneButDoesNotClampLargeValues() {
		ResponderConfig config = new ResponderConfig();
		config.periodicMessages.add(new PeriodicMessageConfig(true, "zero", 0));
		config.periodicMessages.add(new PeriodicMessageConfig(true, "negative", -5));
		config.periodicMessages.add(new PeriodicMessageConfig(true, "large", Integer.MAX_VALUE));

		config.sanitize();

		assertEquals(5, config.periodicMessages.get(0).intervalMinutes);
		assertEquals(5, config.periodicMessages.get(1).intervalMinutes);
		assertEquals(Integer.MAX_VALUE, config.periodicMessages.get(2).intervalMinutes);
	}

	@Test
	void sanitizeRestoresMandatoryMarkerAndNestedRuleValues() {
		ResponderConfig config = new ResponderConfig();
		config.globalMarkers = "[custom]";
		ReplyRule rule = new ReplyRule();
		rule.trigger = null;
		rule.response = null;
		rule.channel = null;
		config.rules.add(rule);

		config.sanitize();

		assertEquals("(!),[custom]", config.globalMarkers);
		assertEquals("", rule.trigger);
		assertEquals("", rule.response);
		assertEquals(ChatChannel.AUTO, rule.channel);
	}

	@SafeVarargs
	private static <T> ArrayList<T> mutableList(T... values) {
		return new ArrayList<>(java.util.Arrays.asList(values));
	}
}
