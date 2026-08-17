package ru.gasada.cndlchatplus;

import java.util.Locale;
import java.util.function.LongSupplier;

public final class OwnMessageGuard {
	private final long windowMillis;
	private final LongSupplier clock;
	private String lastSentText = "";
	private long lastSentAt;

	public OwnMessageGuard(long windowMillis, LongSupplier clock) {
		this.windowMillis = windowMillis;
		this.clock = clock;
	}

	public void recordOutgoing(String message) {
		lastSentText = message;
		lastSentAt = clock.getAsLong();
	}

	public boolean isRecentEcho(String content, String displayed, ActiveTemplateSnapshot template) {
		if (clock.getAsLong() - lastSentAt >= windowMillis || lastSentText.isEmpty()) {
			return false;
		}
		String normalizedContent = normalize(content);
		String normalizedDisplayed = normalize(displayed);
		String normalizedSent = normalize(lastSentText);
		String strippedSent = stripKnownPrefix(normalizedSent, template);
		return normalizedContent.equals(normalizedSent)
				|| normalizedDisplayed.endsWith(normalizedSent)
				|| normalizedContent.equals(strippedSent)
				|| normalizedDisplayed.endsWith(strippedSent);
	}

	public static boolean isLikelyOwnDisplayedMessage(String displayed, String playerName) {
		String ownName = playerName.toLowerCase(Locale.ROOT);
		String normalized = displayed.toLowerCase(Locale.ROOT);
		int chevron = normalized.indexOf('»');
		if (chevron >= 0 && endsWithPlayerName(normalized.substring(0, chevron), ownName)) {
			return true;
		}
		if (normalized.contains("<" + ownName + ">")
				|| normalized.contains("〈" + ownName + "〉")
				|| normalized.contains("‹" + ownName + "›")) {
			return true;
		}
		int timestampEnd = normalized.lastIndexOf(']');
		int colon = normalized.indexOf(':', timestampEnd + 1);
		return colon >= 0 && endsWithPlayerName(normalized.substring(timestampEnd + 1, colon), ownName);
	}

	public void reset() {
		lastSentText = "";
		lastSentAt = 0L;
	}

	private static String stripKnownPrefix(String text, ActiveTemplateSnapshot template) {
		if (template == null) {
			return text;
		}
		String globalPrefix = normalize(template.globalPrefix());
		if (!globalPrefix.isEmpty() && text.startsWith(globalPrefix)) {
			return text.substring(globalPrefix.length()).trim();
		}
		String clanPrefix = normalize(template.clanReplyPrefix());
		if (!clanPrefix.isEmpty() && text.startsWith(clanPrefix)) {
			return text.substring(clanPrefix.length()).trim();
		}
		int commandSpace = text.indexOf(' ');
		if (text.startsWith("/") && commandSpace >= 0) {
			return text.substring(commandSpace + 1).trim();
		}
		return text;
	}

	private static boolean endsWithPlayerName(String prefix, String playerName) {
		String trimmed = prefix.trim();
		if (!trimmed.endsWith(playerName)) {
			return false;
		}
		int start = trimmed.length() - playerName.length();
		return start == 0 || !Character.isLetterOrDigit(trimmed.charAt(start - 1))
				&& trimmed.charAt(start - 1) != '_';
	}

	private static String normalize(String value) {
		return ChatTextNormalizer.normalizeForMatching(value);
	}
}
