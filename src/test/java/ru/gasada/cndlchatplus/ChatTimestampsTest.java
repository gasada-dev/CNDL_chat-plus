package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

final class ChatTimestampsTest {
	@Test
	void applyPrependsGrayTimestamp() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		Component message = Component.literal("привет");

		Component result = timestamps.apply(message);

		assertTrue(result.getString().matches("\\[\\d{2}:\\d{2}] привет"));
	}

	@Test
	void disabledReturnsSameInstance() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> false);
		Component message = Component.literal("привет");
		assertSame(message, timestamps.apply(message));
	}

	@Test
	void restoredMessageIsNotPrefixedTwice() {
		AtomicBoolean enabled = new AtomicBoolean(true);
		ChatTimestamps timestamps = new ChatTimestamps(enabled::get);
		Component message = Component.literal("старое");

		Component restored = timestamps.restored(message, 0L);
		assertTrue(restored.getString().matches("\\[\\d{2}:\\d{2}] старое"));

		Component passedThrough = timestamps.apply(restored);
		assertSame(restored, passedThrough);
	}

	@Test
	void restoredWithoutTimestampsReturnsOriginal() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> false);
		Component message = Component.literal("старое");
		assertSame(message, timestamps.restored(message, 0L));
	}

	@Test
	void resetClearsSkipSetButPatternStillPreventsDoublePrefix() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		Component restored = timestamps.restored(Component.literal("x"), 0L);
		timestamps.resetRuntimeState();

		Component reapplied = timestamps.apply(restored);
		assertSame(restored, reapplied);
	}

	@Test
	void applySkipsMessagesAlreadyStartingWithTimestamp() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		Component alreadyPrefixed = Component.literal("[09:41] Player » привет");
		assertSame(alreadyPrefixed, timestamps.apply(alreadyPrefixed));

		Component notTimestamped = Component.literal("[лс] Player » привет");
		assertTrue(timestamps.apply(notTimestamped).getString()
				.matches("\\[\\d{2}:\\d{2}] \\[лс] Player » привет"));
	}

	@Test
	void explicitTimestampSupportsDuplicateReplacementWithoutSkipState() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		Component counted = Component.literal("Player » hello x3");

		Component result = timestamps.at(counted, 0L);

		assertTrue(result.getString().matches("\\[\\d{2}:\\d{2}] Player » hello x3"));
		assertSame(result, timestamps.apply(result));
	}

	@Test
	void countedMessagePreservesDisplayModificationsAndReplacesOwnTimestamp() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		Component displayed = timestamps.at(Component.literal("[head] Player » hello"), 0L);

		Component result = timestamps.counted(displayed, 3, 60_000L);

		assertTrue(result.getString().matches("\\[\\d{2}:\\d{2}] \\[head] Player » hello x3"));
		assertEquals(1, result.getString().split("\\[head]", -1).length - 1);
	}

	@Test
	void ownershipTrackingIsBoundedAndDuplicateReplacementDoesNotLeak() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		for (int index = 0; index < ResponderConfig.MAX_CHAT_HISTORY_LIMIT * 2 + 10; index++) {
			timestamps.at(Component.literal("message " + index), index);
		}
		assertEquals(ResponderConfig.MAX_CHAT_HISTORY_LIMIT * 2, timestamps.trackedPrefixCount());

		Component duplicate = timestamps.at(Component.literal("duplicate"), 0L);
		for (int count = 2; count < 100; count++) duplicate = timestamps.counted(duplicate, count, count);
		assertEquals(ResponderConfig.MAX_CHAT_HISTORY_LIMIT * 2, timestamps.trackedPrefixCount());
	}
}
