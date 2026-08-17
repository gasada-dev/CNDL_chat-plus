package ru.gasada.chatresponder;

import java.util.regex.Pattern;

public final class ChatMessageTextSanitizer {
	private static final Pattern CHAT_HEAD_LABEL = Pattern.compile(
			"(?iu)\\[[\\p{L}\\p{N}_]{1,32}\\s+head]");

	private ChatMessageTextSanitizer() {
	}

	public static String stripSyntheticLabels(String text) {
		return text == null ? "" : CHAT_HEAD_LABEL.matcher(text).replaceAll("");
	}
}
