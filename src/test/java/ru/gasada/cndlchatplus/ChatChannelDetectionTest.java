package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ChatChannelDetectionTest {
	private ResponderConfig config;
	private ChatResponderEngine engine;

	@BeforeEach
	void setUp() {
		config = ResponderConfig.defaults();
		engine = new ChatResponderEngine(config);
	}

	@Test
	void defaultsUsedByDetectorRemainCharacterized() {
		assertEquals("!", config.globalPrefix);
		assertEquals("(!),[g],[global],[глобальный],глобальный чат", config.globalMarkers);
		assertEquals("(клан),<клан>,〈клан〉,‹клан›", config.clanMarkers);
		assertEquals("[pm],[лс],личное сообщение,шепчет,->,→", config.privateMarkers);
	}

	@Test
	void discordHasPriorityOverPrivateClanAndGlobalMarkers() {
		assertEquals(ChatChannel.GLOBAL, engine.detectChannel(
				"!сообщение", "[Discord] User » [лс] (клан) (!) сообщение"));
	}

	@Test
	void privateMarkerHasPriorityOverClanAndGlobal() {
		assertEquals(ChatChannel.PRIVATE, engine.detectChannel(
				"!сообщение", "[лс] (клан) (!) сообщение"));
	}

	@Test
	void clanMarkerHasPriorityOverGlobalPrefixAndMarkers() {
		assertEquals(ChatChannel.CLAN, engine.detectChannel(
				"!сообщение", "(клан) (!) сообщение"));
	}

	@Test
	void globalPrefixInContentDetectsGlobalChannel() {
		assertEquals(ChatChannel.GLOBAL, engine.detectChannel("!сообщение", "обычное отображение"));
	}

	@Test
	void mandatoryParenthesizedMarkerDetectsGlobalChannel() {
		config.globalMarkers = "";
		assertEquals(ChatChannel.GLOBAL, engine.detectChannel("сообщение", "Игрок (!) сообщение"));
	}

	@Test
	void configuredGlobalMarkerDetectsGlobalChannel() {
		assertEquals(ChatChannel.GLOBAL, engine.detectChannel("сообщение", "[GLOBAL] Игрок: сообщение"));
	}

	@Test
	void missingMarkersFallBackToLocal() {
		assertEquals(ChatChannel.LOCAL, engine.detectChannel("сообщение", "Игрок: сообщение"));
	}
}
