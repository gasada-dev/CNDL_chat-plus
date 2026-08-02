package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import net.minecraft.client.Minecraft;

public final class PeriodicMessageScheduler {
	private final ResponderConfig config;
	private final ChatResponderEngine engine;
	private final LongSupplier clock;
	private final List<State> states = new ArrayList<>();

	public PeriodicMessageScheduler(ResponderConfig config, ChatResponderEngine engine) {
		this(config, engine, System::currentTimeMillis);
	}

	PeriodicMessageScheduler(ResponderConfig config, ChatResponderEngine engine, LongSupplier clock) {
		this.config = config;
		this.engine = engine;
		this.clock = clock;
		for (int index = 0; index < 3; index++) {
			states.add(new State());
		}
	}

	public void tick(Minecraft minecraft) {
		tick(() -> minecraft.getConnection() != null, outgoing -> send(minecraft, outgoing));
	}

	void tick(BooleanSupplier connected, Consumer<String> sender) {
		for (int index = 0; index < states.size(); index++) {
			State state = states.get(index);
			if (index >= config.periodicMessages.size()) {
				state.reset();
				continue;
			}

			PeriodicMessageConfig message = config.periodicMessages.get(index);
			if (!message.enabled || message.message.isBlank() || message.intervalMinutes < 1
					|| !connected.getAsBoolean()) {
				state.reset();
				continue;
			}

			String settings = message.message + '\n' + message.intervalMinutes;
			long now = clock.getAsLong();
			long intervalMillis = message.intervalMinutes * 60_000L;
			if (!settings.equals(state.settings) || state.nextSendAt == 0L) {
				state.settings = settings;
				state.nextSendAt = now + intervalMillis;
				continue;
			}

			if (now >= state.nextSendAt) {
				sender.accept(message.message.trim());
				state.nextSendAt = now + intervalMillis;
			}
		}
	}

	private void send(Minecraft minecraft, String outgoing) {
		engine.recordOutgoing(outgoing);
		OutgoingMessage message = classifyOutgoing(outgoing);
		if (message.type() == OutgoingType.COMMAND) {
			minecraft.getConnection().sendCommand(message.payload());
		} else {
			minecraft.getConnection().sendChat(message.payload());
		}
	}

	static OutgoingMessage classifyOutgoing(String outgoing) {
		return outgoing.startsWith("/")
				? new OutgoingMessage(OutgoingType.COMMAND, outgoing.substring(1))
				: new OutgoingMessage(OutgoingType.CHAT, outgoing);
	}

	enum OutgoingType {
		CHAT,
		COMMAND
	}

	record OutgoingMessage(OutgoingType type, String payload) {
	}

	private static final class State {
		private String settings = "";
		private long nextSendAt;

		private void reset() {
			settings = "";
			nextSendAt = 0L;
		}
	}
}
