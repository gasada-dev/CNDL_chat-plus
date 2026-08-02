package ru.gasada.chatresponder;

import java.util.function.LongSupplier;

public final class DuplicateMessageGuard {
	private final long windowMillis;
	private final LongSupplier clock;
	private String lastFingerprint = "";
	private long lastSeenAt;

	public DuplicateMessageGuard(long windowMillis, LongSupplier clock) {
		this.windowMillis = windowMillis;
		this.clock = clock;
	}

	public boolean isDuplicate(String content, String displayed) {
		long now = clock.getAsLong();
		String fingerprint = normalize(content) + '\n' + normalize(displayed);
		if (fingerprint.equals(lastFingerprint) && now - lastSeenAt < windowMillis) {
			return true;
		}
		lastFingerprint = fingerprint;
		lastSeenAt = now;
		return false;
	}

	public void reset() {
		lastFingerprint = "";
		lastSeenAt = 0L;
	}

	private static String normalize(String value) {
		return ChatTextNormalizer.normalizeForMatching(value);
	}
}
