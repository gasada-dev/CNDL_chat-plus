package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

final class ContextMenuBuilderTest {
	private final ContextMenuBuilder builder = new ContextMenuBuilder();

	@Test
	void systemMessageOnlyAllowsCopy() {
		assertEquals(List.of(ChatContextAction.COPY_MESSAGE), builder.build(null, null));
	}

	@Test
	void discordMessageOnlyAllowsCopying() {
		assertEquals(List.of(ChatContextAction.COPY_NICK, ChatContextAction.COPY_MESSAGE),
				builder.build(new ChatMessageSenderExtractor.Sender("DiscordName", true), capabilities(true)));
	}

	@Test
	void playerWithoutTemplateOnlyAllowsCopying() {
		assertEquals(List.of(ChatContextAction.COPY_NICK, ChatContextAction.COPY_MESSAGE),
				builder.build(new ChatMessageSenderExtractor.Sender("Steve", false), capabilities(false)));
	}

	@Test
	void playerActionsFollowAvailableCommands() {
		ContextMenuBuilder.Capabilities capabilities = new ContextMenuBuilder.Capabilities(
				true, true, true, false, true, false);
		assertEquals(List.of(ChatContextAction.COPY_NICK, ChatContextAction.COPY_MESSAGE,
				ChatContextAction.PRIVATE_MESSAGE, ChatContextAction.ADD_FRIEND, ChatContextAction.IGNORE,
				ChatContextAction.PLAYER_INFO, ChatContextAction.CALL),
				builder.build(new ChatMessageSenderExtractor.Sender("Steve", false), capabilities));
	}

	private static ContextMenuBuilder.Capabilities capabilities(boolean active) {
		return new ContextMenuBuilder.Capabilities(active, true, true, true, true, true);
	}
}
