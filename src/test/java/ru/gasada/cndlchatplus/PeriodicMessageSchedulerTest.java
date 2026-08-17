package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

final class PeriodicMessageSchedulerTest {
	@Test
	void disabledMessageNeverSchedulesOrSends() {
		TestFixture fixture = fixture(new PeriodicMessageConfig(false, "текст", 1));

		fixture.tickConnected();
		fixture.clock.advance(600_000);
		fixture.tickConnected();

		assertTrue(fixture.sent.isEmpty());
	}

	@Test
	void blankMessageNeverSchedulesOrSends() {
		TestFixture fixture = fixture(new PeriodicMessageConfig(true, "   ", 1));

		fixture.tickConnected();
		fixture.clock.advance(600_000);
		fixture.tickConnected();

		assertTrue(fixture.sent.isEmpty());
	}

	@Test
	void firstTickOnlySchedulesAndDoesNotSend() {
		TestFixture fixture = fixture(new PeriodicMessageConfig(true, "текст", 1));

		fixture.tickConnected();

		assertTrue(fixture.sent.isEmpty());
	}

	@Test
	void messageIsNotSentBeforeFullInterval() {
		TestFixture fixture = fixture(new PeriodicMessageConfig(true, "текст", 1));
		fixture.tickConnected();

		fixture.clock.advance(59_999);
		fixture.tickConnected();

		assertTrue(fixture.sent.isEmpty());
	}

	@Test
	void messageIsSentAtIntervalDeadline() {
		TestFixture fixture = fixture(new PeriodicMessageConfig(true, "  текст  ", 1));
		fixture.tickConnected();

		fixture.clock.advance(60_000);
		fixture.tickConnected();

		assertEquals(List.of("текст"), fixture.sent);
	}

	@Test
	void changingTextResetsCountdown() {
		PeriodicMessageConfig message = new PeriodicMessageConfig(true, "первый", 1);
		TestFixture fixture = fixture(message);
		fixture.tickConnected();
		fixture.clock.advance(30_000);
		message.message = "второй";

		fixture.tickConnected();
		fixture.clock.advance(30_000);
		fixture.tickConnected();
		assertTrue(fixture.sent.isEmpty());

		fixture.clock.advance(30_000);
		fixture.tickConnected();
		assertEquals(List.of("второй"), fixture.sent);
	}

	@Test
	void changingIntervalResetsCountdown() {
		PeriodicMessageConfig message = new PeriodicMessageConfig(true, "текст", 1);
		TestFixture fixture = fixture(message);
		fixture.tickConnected();
		fixture.clock.advance(30_000);
		message.intervalMinutes = 2;

		fixture.tickConnected();
		fixture.clock.advance(90_000);
		fixture.tickConnected();
		assertTrue(fixture.sent.isEmpty());

		fixture.clock.advance(30_000);
		fixture.tickConnected();
		assertEquals(List.of("текст"), fixture.sent);
	}

	@Test
	void disconnectResetsCountdown() {
		TestFixture fixture = fixture(new PeriodicMessageConfig(true, "текст", 1));
		fixture.tickConnected();
		fixture.clock.advance(30_000);
		fixture.tickDisconnected();
		fixture.clock.advance(30_000);

		fixture.tickConnected();
		fixture.clock.advance(59_999);
		fixture.tickConnected();
		assertTrue(fixture.sent.isEmpty());

		fixture.clock.advance(1);
		fixture.tickConnected();
		assertEquals(List.of("текст"), fixture.sent);
	}

	@Test
	void commandAndChatClassificationRemainDistinct() {
		assertEquals(new PeriodicMessageScheduler.OutgoingMessage(
				PeriodicMessageScheduler.OutgoingType.COMMAND, "spawn"),
				PeriodicMessageScheduler.classifyOutgoing("/spawn"));
		assertEquals(new PeriodicMessageScheduler.OutgoingMessage(
				PeriodicMessageScheduler.OutgoingType.CHAT, "привет"),
				PeriodicMessageScheduler.classifyOutgoing("привет"));
	}

	@Test
	void threeEntriesKeepIndependentSchedules() {
		TestFixture fixture = fixture(
				new PeriodicMessageConfig(true, "one", 1),
				new PeriodicMessageConfig(true, "two", 2),
				new PeriodicMessageConfig(true, "three", 3));
		fixture.tickConnected();

		fixture.clock.advance(60_000);
		fixture.tickConnected();
		assertEquals(List.of("one"), fixture.sent);

		fixture.clock.advance(60_000);
		fixture.tickConnected();
		assertEquals(List.of("one", "one", "two"), fixture.sent);

		fixture.clock.advance(60_000);
		fixture.tickConnected();
		assertEquals(List.of("one", "one", "two", "one", "three"), fixture.sent);
	}

	private static TestFixture fixture(PeriodicMessageConfig... messages) {
		ResponderConfig config = new ResponderConfig();
		config.periodicMessages = new ArrayList<>(List.of(messages));
		MutableClock clock = new MutableClock();
		PeriodicMessageScheduler scheduler = new PeriodicMessageScheduler(config, null, clock);
		return new TestFixture(scheduler, clock);
	}

	private static final class TestFixture {
		private final PeriodicMessageScheduler scheduler;
		private final MutableClock clock;
		private final List<String> sent = new ArrayList<>();

		private TestFixture(PeriodicMessageScheduler scheduler, MutableClock clock) {
			this.scheduler = scheduler;
			this.clock = clock;
		}

		private void tickConnected() {
			scheduler.tick(() -> true, sent::add);
		}

		private void tickDisconnected() {
			scheduler.tick(() -> false, sent::add);
		}
	}

	private static final class MutableClock implements LongSupplier {
		private long now;

		@Override
		public long getAsLong() {
			return now;
		}

		private void advance(long millis) {
			now += millis;
		}
	}
}
