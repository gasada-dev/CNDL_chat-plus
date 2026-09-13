package ru.gasada.cndlchatplus;

import java.util.Objects;

public final class CustomChatOutgoingService {
	private final ChatTabController tabs;
	private final OutgoingChatService outgoing;

	public CustomChatOutgoingService(ChatTabController tabs, OutgoingChatService outgoing) {
		this.tabs = Objects.requireNonNull(tabs, "tabs");
		this.outgoing = Objects.requireNonNull(outgoing, "outgoing");
	}

	public boolean route(String message) {
		if (!tabs.enabled()) return false;
		String command = commandFor(tabs.activeDefinition(), message);
		if (command == null) return false;
		outgoing.sendCommand(command);
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
