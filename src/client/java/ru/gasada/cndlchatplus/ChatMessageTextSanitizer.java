package ru.gasada.cndlchatplus;

import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;

public final class ChatMessageTextSanitizer {
	private static final Pattern CHAT_HEAD_LABEL = Pattern.compile(
			"(?iu)\\[[\\p{L}\\p{N}_]{1,32}\\s+head]");
	private static final Pattern DISPLAY_FORMATTING = Pattern.compile(
			"(?i)(?:[&§](?:#[0-9a-f]{6}|[0-9a-frk-r]))");

	private ChatMessageTextSanitizer() {
	}

	public static String stripSyntheticLabels(String text) {
		return text == null ? "" : CHAT_HEAD_LABEL.matcher(text).replaceAll("");
	}

	public static String stripDisplayFormatting(String text) {
		return text == null ? "" : DISPLAY_FORMATTING.matcher(text).replaceAll("");
	}

	public static String canonicalMessageText(Component component, ChatTimestamps timestamps) {
		if (component == null) return "";
		Component canonical = timestamps == null ? component : timestamps.withoutOwnPrefix(component);
		return stripSyntheticLabels(canonical.getString());
	}
}
