package ru.gasada.cndlchatplus;

import java.util.Objects;
import java.util.function.Consumer;

public final class CustomChatOutgoingService {
	private final ChatTabController tabs;
	private final OutgoingChatService outgoing;
	private final Consumer<FailedMessage> failureReporter;

	public record FailedMessage(String text, String command, String error) { }

	public CustomChatOutgoingService(ChatTabController tabs, OutgoingChatService outgoing) {
		this(tabs, outgoing, ignored -> { });
	}

	CustomChatOutgoingService(ChatTabController tabs, OutgoingChatService outgoing,
			Consumer<FailedMessage> failureReporter) {
		this.tabs = Objects.requireNonNull(tabs, "tabs");
		this.outgoing = Objects.requireNonNull(outgoing, "outgoing");
		this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter");
	}

	public boolean route(String message) {
		if (!tabs.enabled()) return false;
		String command = commandFor(tabs.activeDefinition(), message);
		if (command == null) return false;
		outgoing.sendCommand(command, result -> {
			if (!result.success()) failureReporter.accept(new FailedMessage(message, command, result.errorMessage()));
		});
		return true;
	}

	static String commandFor(ChatTabDefinition active, String message) {
		if (active == null || !active.custom() || message == null) return null;
		String text = message.trim();
		if (text.isEmpty() || text.startsWith("/")) return null;
		String prefix = active.outgoingPrefix() == null ? "" : active.outgoingPrefix().trim();
		while (prefix.startsWith("/")) prefix = prefix.substring(1).trim();
		if (prefix.isEmpty()) return null;
		return prefix + " " + text;
	}
}
