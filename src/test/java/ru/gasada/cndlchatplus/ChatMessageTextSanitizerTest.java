package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class ChatMessageTextSanitizerTest {
	@Test
	void stripsChatHeadsAccessibilityLabel() {
		assertEquals("[17:17] (!) ZISSKAS » пропстите ночь пж",
				ChatMessageTextSanitizer.stripSyntheticLabels(
						"[17:17] (!) [ZISSKAS head]ZISSKAS » пропстите ночь пж"));
	}

	@Test
	void preservesOrdinaryBrackets() {
		assertEquals("[17:17] [VIP] Steve » message",
				ChatMessageTextSanitizer.stripSyntheticLabels("[17:17] [VIP] Steve » message"));
	}

	@Test
	void canonicalTextRemovesStructurallyOwnedTimestampAndChatHeadsLabel() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		Component stamped = timestamps.at(
				Component.literal("(!) [Steve head]Steve » сообщение"), 1_000L);
		assertEquals("(!) Steve » сообщение",
				ChatMessageTextSanitizer.canonicalMessageText(stamped, timestamps));
	}

	@Test
	void canonicalTextPreservesServerTimestampLikePrefix() {
		assertEquals("[17:17] сообщение сервера",
				ChatMessageTextSanitizer.canonicalMessageText(
						Component.literal("[17:17] сообщение сервера"), new ChatTimestamps(() -> true)));
	}

	@Test
	void canonicalTextPreservesServerOwnedCompositeTimestamp() {
		Component serverMessage = Component.literal("[17:17] ").append(Component.literal("сообщение сервера"));
		assertEquals("[17:17] сообщение сервера",
				ChatMessageTextSanitizer.canonicalMessageText(serverMessage, new ChatTimestamps(() -> true)));
	}

	@Test
	void templateRuntimeResetKeepsOwnershipButDisconnectResetClearsIt() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		Component stamped = timestamps.at(Component.literal("сообщение"), 0L);
		timestamps.resetRuntimeState();
		assertEquals("сообщение", ChatMessageTextSanitizer.canonicalMessageText(stamped, timestamps));

		timestamps.resetConnectionState();
		assertEquals(stamped.getString(), ChatMessageTextSanitizer.canonicalMessageText(stamped, timestamps));
	}
}
