package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ChatChannelDetectionTest {
	private ResponderConfig config;

	@BeforeEach
	void setUp() {
		config = ResponderConfig.defaults();
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
		assertEquals(ChatChannel.GLOBAL, detect(
				"!сообщение", "[Discord] User » [лс] (клан) (!) сообщение"));
	}

	@Test
	void privateMarkerHasPriorityOverClanAndGlobal() {
		assertEquals(ChatChannel.PRIVATE, detect(
				"!сообщение", "[лс] (клан) (!) сообщение"));
	}

	@Test
	void clanMarkerHasPriorityOverGlobalPrefixAndMarkers() {
		assertEquals(ChatChannel.CLAN, detect(
				"!сообщение", "(клан) (!) сообщение"));
	}

	@Test
	void globalPrefixInContentDetectsGlobalChannel() {
		assertEquals(ChatChannel.GLOBAL, detect("!сообщение", "обычное отображение"));
	}

	@Test
	void mandatoryParenthesizedMarkerDetectsGlobalChannel() {
		config.globalMarkers = "";
		assertEquals(ChatChannel.GLOBAL, detect("сообщение", "Игрок (!) сообщение"));
	}

	@Test
	void configuredGlobalMarkerDetectsGlobalChannel() {
		assertEquals(ChatChannel.GLOBAL, detect("сообщение", "[GLOBAL] Игрок: сообщение"));
	}

	@Test
	void missingMarkersFallBackToLocal() {
		assertEquals(ChatChannel.LOCAL, detect("сообщение", "Игрок: сообщение"));
	}

	private ChatChannel detect(String content, String displayed) {
		ActiveTemplateSnapshot template = ActiveTemplateSnapshot.from(
				LegacyConfigToVanillaBoxMigration.fromLegacy(config), 1);
		return new ChatChannelDetector(template, CompiledParserSettings.compile(template.parsers()))
				.detect(content, displayed);
	}
}
