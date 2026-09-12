package ru.gasada.cndlchatplus;

import java.util.Objects;
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
		InputSanitizer.Result sanitized =
				InputSanitizer.validateAndTrim(command, MAX_OUTGOING_LENGTH, "Команда");
		if (!sanitized.valid()) {
			return SendResult.failure(sanitized.errorMessage());
		}
		if (sanitized.value().startsWith("/")) {
			return SendResult.failure("Команда передаётся без начального /");
		}
		return send(sanitized.value(), true);
	}

	private SendResult send(String payload, boolean command) {
		if (!authorized.getAsBoolean() || !transport.connected()) {
			return SendResult.failure("Нет подключения к серверу");
		}
		long queuedGeneration = generation.getAsLong();
		outgoingRecorder.accept(command ? "/" + payload : payload);
		transport.execute(() -> {
			if (!authorized.getAsBoolean() || !transport.connected()
					|| generation.getAsLong() != queuedGeneration) {
				return;
			}
			if (command) {
				transport.sendCommand(payload);
			} else {
				transport.sendChat(payload);
			}
		});
		return SendResult.ok();
	}

	public interface Transport {
		boolean connected();

		void execute(Runnable action);

		void sendChat(String message);

		void sendCommand(String command);
	}

	public record SendResult(boolean success, String errorMessage) {
		private static SendResult ok() {
			return new SendResult(true, "");
		}

		private static SendResult failure(String errorMessage) {
			return new SendResult(false, errorMessage);
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
