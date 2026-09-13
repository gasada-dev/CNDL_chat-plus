package ru.gasada.cndlchatplus;

import java.util.List;

public record ChatTabDefinition(String id, String displayName, ChatTab builtIn, List<ChatTabSource> sources,
		String outgoingPrefix) {
	public ChatTabDefinition {
		sources = List.copyOf(sources);
	}

	static ChatTabDefinition builtIn(ChatTab tab) {
		return new ChatTabDefinition("builtin:" + tab.name(), tab.displayName(), tab, List.of(), "");
	}

	static ChatTabDefinition custom(CustomChatTab tab) {
		return new ChatTabDefinition(tab.id, tab.name, null, tab.sources, tab.outgoingPrefix);
	}

	public boolean custom() {
		return builtIn == null;
	}
}
