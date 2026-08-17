package ru.gasada.chatresponder;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

public final class ChatMessageStore {
	private final IntSupplier limitSupplier;
	private final ArrayDeque<ChatHistoryEntry> entries = new ArrayDeque<>();

	public ChatMessageStore(IntSupplier limitSupplier) {
		this.limitSupplier = Objects.requireNonNull(limitSupplier, "limitSupplier");
	}

	public void add(ChatHistoryEntry entry) {
		entries.addLast(Objects.requireNonNull(entry, "entry"));
		int limit = Math.max(1, limitSupplier.getAsInt());
		while (entries.size() > limit) {
			entries.removeFirst();
		}
	}

	public List<ChatHistoryEntry> snapshot() {
		return List.copyOf(entries);
	}

	public void clear() {
		entries.clear();
	}
}
