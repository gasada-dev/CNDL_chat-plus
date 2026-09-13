package ru.gasada.cndlchatplus;

import java.util.List;

public enum ChatTabSource {
	GLOBAL("Глобал"),
	LOCAL("Локал"),
	CLAN("Клан"),
	PRIVATE("ЛС"),
	DISCORD("Discord"),
	SYSTEM("Система"),
	VOICE("ГС");

	private static final String VOICE_MARKER = "(войс)";

	private final String displayName;

	ChatTabSource(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	static ChatTabSource classify(ChatTab classified, String displayed) {
		if (ChatTextNormalizer.normalizeForMatching(displayed).contains(VOICE_MARKER)) return VOICE;
		return switch (classified) {
			case GLOBAL -> GLOBAL;
			case LOCAL -> LOCAL;
			case CLAN -> CLAN;
			case PRIVATE -> PRIVATE;
			case DISCORD -> DISCORD;
			case SYSTEM -> SYSTEM;
			case ALL -> null;
		};
	}

	static List<ChatTabSource> fromLegacyMarker(String marker) {
		return marker != null && ChatTextNormalizer.normalizeForMatching(marker).equals(VOICE_MARKER)
				? List.of(VOICE) : List.of();
	}
}
