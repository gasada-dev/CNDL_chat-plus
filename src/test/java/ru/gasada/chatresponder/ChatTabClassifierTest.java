package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ChatTabClassifierTest {
	private ChatTabClassifier classifier;

	@BeforeEach
	void setUp() {
		classifier = new ChatTabClassifier(ServerTemplateRuntime.fromLegacyConfig(ResponderConfig.defaults()));
	}

	@Test
	void gameMessagesAreSystemUnlessDiscord() {
		assertEquals(ChatTab.SYSTEM, classifier.classify("Игрок зашёл на сервер", true));
	}

	@Test
	void markersBeatPacketTypeForChatSentAsSystemMessages() {
		assertEquals(ChatTab.GLOBAL, classifier.classify("(!) Player » привет", true));
		assertEquals(ChatTab.GLOBAL, classifier.classify("!привет", true));
		assertEquals(ChatTab.CLAN, classifier.classify("(клан) Player » привет", true));
		assertEquals(ChatTab.PRIVATE, classifier.classify("[лс] Player » привет", true));
	}

	@Test
	void localMarkersBeatSystemFallback() {
		assertEquals(ChatTab.LOCAL, classifier.classify("〈Л〉 CNDL gasada » в", true));
		assertEquals(ChatTab.LOCAL, classifier.classify("(л) Player » привет", true));
		assertEquals(ChatTab.SYSTEM, classifier.classify("Вас никто не услышал.", true));
	}

	@Test
	void discordHasPriorityOverEverything() {
		assertEquals(ChatTab.DISCORD, classifier.classify("[Discord] User » [лс] (клан) (!) текст", false));
		assertEquals(ChatTab.DISCORD, classifier.classify("[Discord] User » текст", true));
	}

	@Test
	void playerMessagesMapThroughChannelDetector() {
		assertEquals(ChatTab.PRIVATE, classifier.classify("[лс] Player » привет", false));
		assertEquals(ChatTab.CLAN, classifier.classify("(клан) Player » привет", false));
		assertEquals(ChatTab.GLOBAL, classifier.classify("(!) Player » привет", false));
		assertEquals(ChatTab.GLOBAL, classifier.classify("!привет", false));
		assertEquals(ChatTab.LOCAL, classifier.classify("Player » привет", false));
	}

	@Test
	void missingTemplateFallsBackToLocalOrSystem() {
		ChatTabClassifier empty = new ChatTabClassifier(new ServerTemplateRuntime(new TemplateSwitchCoordinator()));
		assertEquals(ChatTab.LOCAL, empty.classify("Player » привет", false));
		assertEquals(ChatTab.SYSTEM, empty.classify("Сообщение сервера", true));
	}
}
