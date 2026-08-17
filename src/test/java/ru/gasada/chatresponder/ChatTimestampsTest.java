package ru.gasada.chatresponder;

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
}
