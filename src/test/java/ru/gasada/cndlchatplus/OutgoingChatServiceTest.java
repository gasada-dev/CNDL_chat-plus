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
	void immediateSendAndExecutorFailuresAreReportedExactlyOnce() {
		List<OutgoingChatService.SendResult> results = new ArrayList<>();
		for (boolean failInExecutor : List.of(false, true)) {
			OutgoingChatService.Transport transport = new OutgoingChatService.Transport() {
				@Override public boolean connected() { return true; }
				@Override public void execute(Runnable action) {
					if (failInExecutor) throw new IllegalStateException("executor");
					action.run();
				}
				@Override public void sendChat(String text) { throw new IllegalStateException("transport"); }
				@Override public void sendCommand(String text) { throw new IllegalStateException("transport"); }
			};
			OutgoingChatService outgoing = new OutgoingChatService(transport, ignored -> { });
			assertEquals(OutgoingChatService.SendState.FAILED, outgoing.sendCommand("gc hello", results::add).state());
		}
		assertEquals(2, results.size());
	}

	@Test
	void completionDistinguishesQueuedSentStaleAndTransportFailure() {
		DeferredTransport transport = new DeferredTransport();
		long[] generation = {1};
		OutgoingChatService outgoing = new OutgoingChatService(transport, ignored -> { }, () -> true, () -> generation[0]);
		List<OutgoingChatService.SendResult> results = new ArrayList<>();
		assertEquals(OutgoingChatService.SendState.QUEUED, outgoing.sendCommand("gc hello", results::add).state());
		assertTrue(results.isEmpty());
		transport.queued.removeFirst().run();
		assertEquals(OutgoingChatService.SendState.SENT, results.getLast().state());
		outgoing.sendCommand("gc stale", results::add);
		generation[0]++;
		transport.queued.removeFirst().run();
		assertFalse(results.getLast().success());
		transport.fail = true;
		outgoing.sendCommand("gc failed", results::add);
		transport.queued.removeFirst().run();
		assertFalse(results.getLast().success());
		assertEquals(3, results.size());
		assertEquals(List.of("gc hello"), transport.commands);
	}

	@Test
	void lastSeenUpdateDoesNotCancelQueuedSend() {
		DeferredTransport transport = new DeferredTransport();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(VanillaBoxConfig.empty());
		OutgoingChatService outgoing = new OutgoingChatService(transport, ignored -> { }, () -> true, runtime::generation);
		outgoing.sendCommand("claimfly");
		runtime.updateLastSeen("Alice", "сейчас");
		transport.queued.removeFirst().run();
		assertEquals(List.of("claimfly"), transport.commands);
	}

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

	@Test
	void queuedSendRejectsCapturedRuntimeGenerationAfterDisconnect() {
		DeferredTransport transport = new DeferredTransport();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(VanillaBoxConfig.empty());
		OutgoingChatService outgoing = new OutgoingChatService(
				transport, ignored -> { }, () -> true, runtime::generation);

		assertTrue(outgoing.sendCommand("claimfly").success());
		runtime.clear();
		transport.queued.removeFirst().run();

		assertTrue(transport.commands.isEmpty());
	}

	private static final class DeferredTransport implements OutgoingChatService.Transport {
		private boolean fail;
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
			if (fail) throw new IllegalStateException("test failure");
			commands.add(command);
		}
	}
}
