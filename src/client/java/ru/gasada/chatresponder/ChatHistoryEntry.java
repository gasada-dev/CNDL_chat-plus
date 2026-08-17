package ru.gasada.chatresponder;

public record ChatHistoryEntry(long timestamp, String json, ChatTab tab) {
	public ChatHistoryEntry(long timestamp, String json) {
		this(timestamp, json, null);
	}
}
