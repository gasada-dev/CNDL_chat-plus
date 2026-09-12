package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

final class OutgoingChatServiceTest {
	@Test
	void inactiveAuthorizationRejectsBeforeRecorderAndQueue() {
		DeferredTransport transport = new DeferredTransport();
		List<String> recorded = new ArrayList<>();
		OutgoingChatService outgoing = new OutgoingChatService(transport, recorded::add, () -> false);

		assertFalse(outgoing.sendCommand("claimfly").success());
		assertTrue(recorded.isEmpty());
		assertTrue(transport.queued.isEmpty());
		assertTrue(transport.commands.isEmpty());
	}

	@Test
	void queuedSendRechecksAuthorizationBeforeMinecraftTransport() {
		DeferredTransport transport = new DeferredTransport();
		AtomicBoolean authorized = new AtomicBoolean(true);
		List<String> recorded = new ArrayList<>();
		OutgoingChatService outgoing = new OutgoingChatService(transport, recorded::add, authorized::get);

		assertTrue(outgoing.sendChat("hello").success());
		assertEquals(List.of("hello"), recorded);
		assertEquals(1, transport.queued.size());

		authorized.set(false);
		transport.queued.removeFirst().run();
		assertTrue(transport.chats.isEmpty());
	}

	private static final class DeferredTransport implements OutgoingChatService.Transport {
		private final List<Runnable> queued = new ArrayList<>();
		private final List<String> chats = new ArrayList<>();
		private final List<String> commands = new ArrayList<>();

		@Override
		public boolean connected() {
			return true;
		}

		@Override
		public void execute(Runnable action) {
			queued.add(action);
		}

		@Override
		public void sendChat(String message) {
			chats.add(message);
		}

		@Override
		public void sendCommand(String command) {
			commands.add(command);
		}
	}
}
