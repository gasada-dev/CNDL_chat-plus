package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

final class ChatDuplicateCollapserTest {
	@Test
	void disabledCollapserClearsPreviousSeries() {
		AtomicBoolean enabled = new AtomicBoolean(true);
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser(enabled::get);
		Component message = Component.literal("same");
		collapser.incoming(message, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(message);

		enabled.set(false);
		assertFalse(collapser.incoming(Component.literal("same"), ChatDuplicateCollapser.Source.CHAT).duplicate());
		enabled.set(true);
		assertFalse(collapser.incoming(Component.literal("same"), ChatDuplicateCollapser.Source.CHAT).duplicate());
	}

	@Test
	void countsOnlyConsecutiveStructurallyEqualMessages() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		Component first = Component.literal("Player » hello");
		Component displayed = Component.literal("[10:00] Player » hello");

		assertFalse(collapser.incoming(first, ChatDuplicateCollapser.Source.CHAT).duplicate());
		collapser.observeDisplayed(displayed);
		ChatDuplicateCollapser.Decision second = collapser.incoming(
				Component.literal("Player » hello"), ChatDuplicateCollapser.Source.CHAT);
		assertTrue(second.duplicate());
		assertSame(displayed, second.expectedDisplayed());
		assertEquals("Player » hello x2", second.replacementRaw().getString());

		Component displayedTwice = Component.literal("[10:01] Player » hello x2");
		collapser.replacementSucceeded(second.replacementRaw(), displayedTwice, second.count());
		ChatDuplicateCollapser.Decision third = collapser.incoming(
				Component.literal("Player » hello"), ChatDuplicateCollapser.Source.CHAT);
		assertSame(second.replacementRaw(), third.expectedRaw());
		assertEquals("Player » hello x3", third.replacementRaw().getString());
	}

	@Test
	void interveningMessageStartsNewSeries() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		Component first = Component.literal("A");
		collapser.incoming(first, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(first);

		Component intervening = Component.literal("B");
		assertFalse(collapser.incoming(intervening, ChatDuplicateCollapser.Source.CHAT).duplicate());
		collapser.observeDisplayed(intervening);
		assertFalse(collapser.incoming(Component.literal("A"),
				ChatDuplicateCollapser.Source.CHAT).duplicate());
	}

	@Test
	void chatAndGameMessagesDoNotShareSeries() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		Component chat = Component.literal("same");
		collapser.incoming(chat, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(chat);

		assertFalse(collapser.incoming(Component.literal("same"),
				ChatDuplicateCollapser.Source.GAME).duplicate());
	}

	@Test
	void styleDifferencePreventsCollapse() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		Component first = Component.literal("same").withStyle(ChatFormatting.RED);
		collapser.incoming(first, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(first);

		assertFalse(collapser.incoming(Component.literal("same").withStyle(ChatFormatting.BLUE),
				ChatDuplicateCollapser.Source.CHAT).duplicate());
	}

	@Test
	void invisibleStyleMetadataDoesNotPreventCollapse() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		Component first = Component.literal("same").withStyle(style -> style.withInsertion("first"));
		collapser.incoming(first, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(first);

		Component second = Component.literal("same").withStyle(style -> style.withInsertion("second"));
		assertTrue(collapser.incoming(second, ChatDuplicateCollapser.Source.CHAT).duplicate());
	}

	@Test
	void displayModMayReplaceIncomingComponent() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		Component raw = Component.literal("Player » hello");
		Component modified = Component.literal("[head] Player » hello");
		collapser.incoming(raw, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(modified);

		ChatDuplicateCollapser.Decision duplicate = collapser.incoming(
				Component.literal("Player » hello"), ChatDuplicateCollapser.Source.CHAT);
		assertTrue(duplicate.duplicate());
		assertSame(modified, duplicate.expectedDisplayed());
		assertSame(modified, duplicate.displayedBase());
	}

	@Test
	void unrelatedClientDisplayBreaksSeries() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		Component first = Component.literal("A");
		collapser.incoming(first, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(first);
		collapser.observeDisplayed(Component.literal("client message"));

		assertFalse(collapser.incoming(Component.literal("A"),
				ChatDuplicateCollapser.Source.CHAT).duplicate());
	}

	@Test
	void laterDisplayMutationDoesNotChangeRawComparison() {
		ChatDuplicateCollapser collapser = new ChatDuplicateCollapser();
		MutableComponent first = Component.literal("A");
		collapser.incoming(first, ChatDuplicateCollapser.Source.CHAT);
		collapser.observeDisplayed(first);
		first.append(" [head]");

		assertTrue(collapser.incoming(Component.literal("A"),
				ChatDuplicateCollapser.Source.CHAT).duplicate());
	}
}
