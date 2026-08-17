package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import net.minecraft.client.Minecraft;

public final class PeriodicMessageScheduler {
	private final ResponderConfig config;
	private final ServerTemplateRuntime templateRuntime;
	private final OutgoingChatService outgoingChatService;
	private final LongSupplier clock;
	private final List<State> states = new ArrayList<>();

	public PeriodicMessageScheduler(ResponderConfig config, ChatResponderEngine engine) {
		this(config, engine, System::currentTimeMillis);
	}

	PeriodicMessageScheduler(ResponderConfig config, ChatResponderEngine engine, LongSupplier clock) {
		this.config = config;
		this.templateRuntime = null;
		this.outgoingChatService = engine == null ? null : engine.outgoingChatService();
		this.clock = clock;
		initializeStates();
	}

	public PeriodicMessageScheduler(ServerTemplateRuntime templateRuntime,
			OutgoingChatService outgoingChatService) {
		this(templateRuntime, outgoingChatService, System::currentTimeMillis);
	}

	PeriodicMessageScheduler(ServerTemplateRuntime templateRuntime, OutgoingChatService outgoingChatService,
			LongSupplier clock) {
		this.config = null;
		this.templateRuntime = templateRuntime;
		this.outgoingChatService = outgoingChatService;
		this.clock = clock;
		initializeStates();
	}

	private void initializeStates() {
		for (int index = 0; index < PeriodicMessageConfig.MAX_PERIODIC_MESSAGES; index++) {
			states.add(new State());
		}
	}

	public void tick(Minecraft minecraft) {
		tick(() -> minecraft.getConnection() != null, outgoing -> send(minecraft, outgoing));
	}

	void tick(BooleanSupplier connected, Consumer<String> sender) {
		List<ActiveTemplateSnapshot.PeriodicSnapshot> messages = activeMessages();
		for (int index = 0; index < states.size(); index++) {
			State state = states.get(index);
			if (index >= messages.size()) {
				state.reset();
				continue;
			}

			ActiveTemplateSnapshot.PeriodicSnapshot message = messages.get(index);
			if (!message.enabled() || message.message().isBlank() || message.intervalMinutes() < 1
					|| !connected.getAsBoolean()) {
				state.reset();
				continue;
			}

			String settings = message.message() + '\n' + message.intervalMinutes();
			long now = clock.getAsLong();
			long intervalMillis = message.intervalMinutes() * 60_000L;
			if (!settings.equals(state.settings) || state.nextSendAt == 0L) {
				state.settings = settings;
				state.nextSendAt = now + intervalMillis;
				continue;
			}

			if (now >= state.nextSendAt) {
				sender.accept(message.message().trim());
				state.nextSendAt = now + intervalMillis;
			}
		}
	}

	private List<ActiveTemplateSnapshot.PeriodicSnapshot> activeMessages() {
		if (templateRuntime != null) {
			return templateRuntime.activeSnapshot()
					.map(ActiveTemplateSnapshot::periodicMessages).orElse(List.of());
		}
		return config.periodicMessages.stream()
				.limit(PeriodicMessageConfig.MAX_PERIODIC_MESSAGES)
				.map(message -> new ActiveTemplateSnapshot.PeriodicSnapshot(
						message.enabled, message.message, message.intervalMinutes))
				.toList();
	}

	public void resetRuntimeState() {
		states.forEach(State::reset);
	}

	private void send(Minecraft minecraft, String outgoing) {
		if (outgoingChatService == null) {
			throw new IllegalStateException("OutgoingChatService is required for Minecraft sending");
		}
		OutgoingMessage message = classifyOutgoing(outgoing);
		if (message.type() == OutgoingType.COMMAND) {
			outgoingChatService.sendCommand(message.payload());
		} else {
			outgoingChatService.sendChat(message.payload());
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
