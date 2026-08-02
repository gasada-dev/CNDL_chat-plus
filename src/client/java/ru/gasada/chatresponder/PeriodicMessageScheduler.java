package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

public final class PeriodicMessageScheduler {
	private final ResponderConfig config;
	private final ChatResponderEngine engine;
	private final List<State> states = new ArrayList<>();

	public PeriodicMessageScheduler(ResponderConfig config, ChatResponderEngine engine) {
		this.config = config;
		this.engine = engine;
		for (int index = 0; index < 3; index++) {
			states.add(new State());
		}
	}

	public void tick(Minecraft minecraft) {
		for (int index = 0; index < states.size(); index++) {
			State state = states.get(index);
			if (index >= config.periodicMessages.size()) {
				state.reset();
				continue;
			}

			PeriodicMessageConfig message = config.periodicMessages.get(index);
			if (!message.enabled || message.message.isBlank() || message.intervalMinutes < 1
					|| minecraft.getConnection() == null) {
				state.reset();
				continue;
			}

			String settings = message.message + '\n' + message.intervalMinutes;
			long now = System.currentTimeMillis();
			long intervalMillis = message.intervalMinutes * 60_000L;
			if (!settings.equals(state.settings) || state.nextSendAt == 0L) {
				state.settings = settings;
				state.nextSendAt = now + intervalMillis;
				continue;
			}

			if (now >= state.nextSendAt) {
				send(minecraft, message.message.trim());
				state.nextSendAt = now + intervalMillis;
			}
		}
	}

	private void send(Minecraft minecraft, String outgoing) {
		engine.recordOutgoing(outgoing);
		if (outgoing.startsWith("/")) {
			minecraft.getConnection().sendCommand(outgoing.substring(1));
		} else {
			minecraft.getConnection().sendChat(outgoing);
		}
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
