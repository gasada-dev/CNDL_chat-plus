package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TemplateParserSettingsTest {
	@Test
	void vanillaBoxDiscordParserPreservesMarkerAndSenderFormats() {
		CompiledParserSettings compiled = CompiledParserSettings.compile(ParserSettings.vanillaBoxDefaults());
		DiscordMessageParser parser = new DiscordMessageParser(compiled);
		DiscordMessageParser.DiscordMessageInfo info = parser.parse("[Discord] Prefix User_42 » hello");
		assertTrue(info.discordMessage());
		assertEquals("User_42", info.sender());
		assertTrue(parser.parse("〈discord〉 Игрок » текст").discordMessage());
		assertFalse(parser.parse("обычное сообщение").discordMessage());
	}

	@Test
	void invalidSinglePatternDoesNotBreakOtherCompiledParsers() {
		ParserSettings source = ParserSettings.vanillaBoxDefaults();
		source.discordMarkerPattern = "[broken";
		CompiledParserSettings compiled = CompiledParserSettings.compile(source);
		assertTrue(compiled.discordMarker().isEmpty());
		assertTrue(compiled.lastSeen().isPresent());
		assertTrue(new FriendLookupParser(compiled).parse("Был онлайн: вчера").type()
				== FriendLookupParser.MessageType.LAST_SEEN);
	}

	@Test
	void customLookupPatternsComeOnlyFromTemplate() {
		ParserSettings source = new ParserSettings();
		source.lastSeenPattern = "(?i)seen=([^;]+)";
		source.inactivePattern = "(?i)idle=([^;]+)";
		source.lookupEndPattern = "(?i)done";
		source.lookupOutputPattern = "(?i)profile";
		source.timestampOnlyPattern = "\\d{2}:\\d{2}";
		FriendLookupParser parser = new FriendLookupParser(CompiledParserSettings.compile(source));

		assertEquals("yesterday", parser.parse("seen=yesterday").value());
		assertEquals(FriendLookupParser.MessageType.UNRELATED,
				parser.parse("Был онлайн: старый fallback").type());
		assertEquals(FriendLookupParser.MessageType.LOOKUP_END, parser.parse("done").type());
	}

	@Test
	void channelDetectorUsesActiveTemplatePrefixesMarkersAndDiscordPattern() {
		ServerTemplate template = ServerTemplate.empty("custom", "Custom");
		template.globalPrefix = "$";
		template.globalMarkers = "[world]";
		template.clanMarkers = "[team]";
		template.privateMarkers = "[direct]";
		template.parsers = new ParserSettings();
		template.parsers.discordMarkerPattern = "(?i)\\[bridge]";
		template.parsers.discordNamePattern = "[A-Za-z0-9_]+";
		template.parsers.replyCandidateSeparators.add(" :: ");
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ActiveTemplateSnapshot snapshot = runtime.switchTo(template);
		ChatChannelDetector detector = new ChatChannelDetector(snapshot, runtime.compiledParsers().orElseThrow());

		assertEquals(ChatChannel.GLOBAL, detector.detect("plain", "[bridge] User :: text"));
		assertEquals(ChatChannel.PRIVATE, detector.detect("plain", "[direct] [team] [world]"));
		assertEquals(ChatChannel.CLAN, detector.detect("plain", "[team] [world]"));
		assertEquals(ChatChannel.GLOBAL, detector.detect("$text", "plain"));
		assertEquals(ChatChannel.LOCAL, detector.detect("!old", "[global] old fallback"));
	}

	@Test
	void parserPatternValidatorLimitsLengthAndRequiredCapture() {
		assertFalse(ParserPatternValidator.validate("[broken", false).valid());
		assertFalse(ParserPatternValidator.validate("no capture", true).valid());
		assertFalse(ParserPatternValidator.validate("a".repeat(4097), false).valid());
		assertTrue(ParserPatternValidator.validate("value=(.+)", true).valid());
	}
}
