package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;

public final class CustomChatTab {
	public static final String DEFAULT_VOICE_ID = "default-voice-chat";

	public String id;
	public String name;
	public List<ChatTabSource> sources;
	public String outgoingPrefix;

	public CustomChatTab() {
		this("", "Вкладка", List.of(), "");
	}

	public CustomChatTab(String id, String name, List<ChatTabSource> sources, String outgoingPrefix) {
		this.id = id;
		this.name = name;
		this.sources = new ArrayList<>(sources == null ? List.of() : sources);
		this.outgoingPrefix = outgoingPrefix;
	}

	static CustomChatTab defaultVoice() {
		return new CustomChatTab(DEFAULT_VOICE_ID, "ГС чат", List.of(ChatTabSource.VOICE), "/gc");
	}

	CustomChatTab copy() {
		return new CustomChatTab(id, name, sources, outgoingPrefix);
	}
}
