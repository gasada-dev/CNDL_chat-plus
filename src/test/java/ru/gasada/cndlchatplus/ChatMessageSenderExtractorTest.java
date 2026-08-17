package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChatMessageSenderExtractorTest {
	private final ChatMessageSenderExtractor extractor = new ChatMessageSenderExtractor();
	private final CompiledParserSettings parsers = CompiledParserSettings.compile(ParserSettings.vanillaBoxDefaults());

	@Test
	void extractsLastValidPlayerBeforeConfiguredSeparator() {
		ChatMessageSenderExtractor.Sender sender = extractor
				.extract("[12:34] (!) LGN @ kzz » сообщение", parsers).orElseThrow();

		assertEquals("kzz", sender.name());
		assertFalse(sender.discord());
	}

	@Test
	void extractsPlayerAfterChatHeadLabelIsRemoved() {
		String text = ChatMessageTextSanitizer.stripSyntheticLabels(
				"[17:17] (!) [ZISSKAS head]ZISSKAS » пропстите ночь пж");

		assertEquals("ZISSKAS", extractor.extract(text, parsers).orElseThrow().name());
	}

	@Test
	void extractsPrivateAndDiscordNames() {
		assertEquals("Steve", extractor.extract("[лс] Steve → привет", parsers).orElseThrow().name());
		ChatMessageSenderExtractor.Sender discord = extractor
				.extract("[Discord] Name_1 » привет", parsers).orElseThrow();
		assertEquals("Name_1", discord.name());
		assertTrue(discord.discord());
	}

	@Test
	void rejectsMessagesWithoutConfiguredSeparator() {
		assertTrue(extractor.extract("Steve вошёл в игру", parsers).isEmpty());
	}
}
