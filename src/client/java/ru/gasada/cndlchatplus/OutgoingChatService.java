package ru.gasada.cndlchatplus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import net.minecraft.client.Minecraft;

public final class OutgoingChatService {
	public static final int MAX_OUTGOING_LENGTH = 256;

	private final Transport transport;
	private final Consumer<String> outgoingRecorder;
	private final BooleanSupplier authorized;
	private final LongSupplier generation;

	public OutgoingChatService(Transport transport, Consumer<String> outgoingRecorder) {
		this(transport, outgoingRecorder, () -> true);
	}

	public OutgoingChatService(Transport transport, Consumer<String> outgoingRecorder,
			BooleanSupplier authorized) {
		this(transport, outgoingRecorder, authorized, () -> 0L);
	}

	OutgoingChatService(Transport transport, Consumer<String> outgoingRecorder,
			BooleanSupplier authorized, LongSupplier generation) {
		this.transport = Objects.requireNonNull(transport, "transport");
		this.outgoingRecorder = Objects.requireNonNull(outgoingRecorder, "outgoingRecorder");
		this.authorized = Objects.requireNonNull(authorized, "authorized");
		this.generation = Objects.requireNonNull(generation, "generation");
	}

	static OutgoingChatService forMinecraft(Consumer<String> outgoingRecorder, LongSupplier generation) {
		return new OutgoingChatService(new MinecraftTransport(), outgoingRecorder,
				CndlChatPlusClient.CONNECTION_GATE::active, generation);
	}

	public SendResult sendChat(String message) {
		InputSanitizer.Result sanitized =
				InputSanitizer.validateAndTrim(message, MAX_OUTGOING_LENGTH, "Сообщение");
		if (!sanitized.valid()) {
			return SendResult.failure(sanitized.errorMessage());
		}
		return send(sanitized.value(), false);
	}

	public SendResult sendCommand(String command) {
		return sendCommand(command, ignored -> { });
	}

	public SendResult sendCommand(String command, Consumer<SendResult> completion) {
		InputSanitizer.Result sanitized =
				InputSanitizer.validateAndTrim(command, MAX_OUTGOING_LENGTH, "Команда");
		if (!sanitized.valid()) {
			return completeFailure(completion, sanitized.errorMessage());
		}
		if (sanitized.value().startsWith("/")) {
			return completeFailure(completion, "Команда передаётся без начального /");
		}
		return send(sanitized.value(), true, completion);
	}

	private SendResult send(String payload, boolean command) {
		return send(payload, command, ignored -> { });
	}

	private static SendResult completeFailure(Consumer<SendResult> completion, String message) {
		SendResult failed = SendResult.failure(message);
		completion.accept(failed);
		return failed;
	}

	private SendResult send(String payload, boolean command, Consumer<SendResult> completion) {
		if (!authorized.getAsBoolean() || !transport.connected()) {
			return completeFailure(completion, "Нет подключения к серверу");
		}
		long queuedGeneration = generation.getAsLong();
		outgoingRecorder.accept(command ? "/" + payload : payload);
		AtomicReference<SendResult> completed = new AtomicReference<>();
		Consumer<SendResult> finish = result -> {
			if (completed.compareAndSet(null, result)) completion.accept(result);
		};
		try {
			transport.execute(() -> {
				if (!authorized.getAsBoolean() || !transport.connected()) {
					completeFailure(finish, "Подключение потеряно до отправки");
					return;
				}
				if (generation.getAsLong() != queuedGeneration) {
					completeFailure(finish, "Подключение изменилось до отправки");
					return;
				}
				try {
					if (command) transport.sendCommand(payload);
					else transport.sendChat(payload);
				} catch (RuntimeException error) {
					completeFailure(finish, "Не удалось отправить сообщение");
					return;
				}
				finish.accept(new SendResult(SendState.SENT, ""));
			});
		} catch (RuntimeException error) {
			if (completed.get() != null) throw error;
			return completeFailure(finish, "Не удалось поставить сообщение в очередь");
		}
		SendResult immediate = completed.get();
		return immediate != null ? immediate : new SendResult(SendState.QUEUED, "");
	}

	public interface Transport {
		boolean connected();

		void execute(Runnable action);

		void sendChat(String message);

		void sendCommand(String command);
	}

	public enum SendState { QUEUED, SENT, FAILED }

	public record SendResult(SendState state, String errorMessage) {
		public boolean success() { return state != SendState.FAILED; }

		private static SendResult failure(String errorMessage) {
			return new SendResult(SendState.FAILED, errorMessage);
		}
	}

	private static final class MinecraftTransport implements Transport {
		@Override
		public boolean connected() {
			return Minecraft.getInstance().getConnection() != null;
		}

		@Override
		public void execute(Runnable action) {
			Minecraft.getInstance().execute(action);
		}

		@Override
		public void sendChat(String message) {
			Minecraft.getInstance().getConnection().sendChat(message);
		}

		@Override
		public void sendCommand(String command) {
			Minecraft.getInstance().getConnection().sendCommand(command);
		}
	}
}
