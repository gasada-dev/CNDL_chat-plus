package ru.gasada.cndlchatplus;

public record ChatBookmark(String id, long savedAtMillis, Long messageTimestampMillis,
		String channel, String sender, String text) {
}
