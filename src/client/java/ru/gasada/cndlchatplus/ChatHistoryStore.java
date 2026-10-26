package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public final class ChatHistoryStore {
	static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
	private static final int MAX_ENTRY_LENGTH = 1_048_576;
	private static final Gson GSON = new Gson();
	private static final Type ENTRY_LIST_TYPE = new TypeToken<List<ChatHistoryEntry>>() {
	}.getType();

	private final Path directory;
	private final Set<String> preserveBeforeSave = new HashSet<>();

	public ChatHistoryStore(Path directory) {
		this.directory = directory;
	}

	public static String fileKey(String normalizedAddress) {
		return normalizedAddress.replaceAll("[^A-Za-z0-9.\\-]", "_");
	}

	public List<ChatHistoryEntry> load(String serverKey) {
		Path path = pathFor(serverKey);
		if (Files.notExists(path)) {
			return List.of();
		}
		try {
			if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("history file is too large");
			byte[] bytes;
			try (var input = Files.newInputStream(path)) {
				bytes = input.readNBytes(MAX_FILE_BYTES + 1);
			}
			if (bytes.length > MAX_FILE_BYTES) throw new IOException("history file is too large");
			JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
			if (!root.isJsonArray()) throw new IOException("history must be an array");
			List<ChatHistoryEntry> entries = new ArrayList<>();
			int first = Math.max(0, root.getAsJsonArray().size() - ResponderConfig.MAX_CHAT_HISTORY_LIMIT);
			if (first > 0) preserveBeforeSave.add(serverKey);
			for (int index = first; index < root.getAsJsonArray().size(); index++) {
				try {
					ChatHistoryEntry entry = GSON.fromJson(root.getAsJsonArray().get(index), ChatHistoryEntry.class);
					if (entry == null || entry.json() == null || entry.json().isBlank()
							|| entry.json().length() > MAX_ENTRY_LENGTH) {
						preserveBeforeSave.add(serverKey);
					} else {
						entries.add(entry);
					}
				} catch (RuntimeException malformed) {
					preserveBeforeSave.add(serverKey);
				}
			}
			return entries;
		} catch (Exception exception) {
			preserveBeforeSave.add(serverKey);
			CndlChatPlusClient.LOGGER.warn("Не удалось прочитать историю чата {}: {}",
					path.getFileName(), exception.getClass().getSimpleName());
			return List.of();
		}
	}

	public boolean save(String serverKey, List<ChatHistoryEntry> entries) {
		Path path = pathFor(serverKey);
		Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(directory);
			if (entries.size() > ResponderConfig.MAX_CHAT_HISTORY_LIMIT) throw new IOException("too many entries");
			for (ChatHistoryEntry entry : entries) {
				if (entry == null || entry.json() == null || entry.json().length() > MAX_ENTRY_LENGTH) {
					throw new IOException("invalid or oversized history entry");
				}
			}
			byte[] bytes = GSON.toJson(entries, ENTRY_LIST_TYPE).getBytes(StandardCharsets.UTF_8);
			if (bytes.length > MAX_FILE_BYTES) throw new IOException("history file is too large");
			if (preserveBeforeSave.contains(serverKey) && !Files.notExists(path)) {
				if (!Files.isRegularFile(path)) throw new IOException("history source is not a regular file");
				Files.copy(path, path.resolveSibling(path.getFileName() + ".unreadable-" + UUID.randomUUID() + ".bak"));
				preserveBeforeSave.remove(serverKey);
			}
			Files.write(temporaryPath, bytes);
			try {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
			}
			preserveBeforeSave.remove(serverKey);
			return true;
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(temporaryPath);
			} catch (IOException cleanupError) {
				exception.addSuppressed(cleanupError);
			}
			CndlChatPlusClient.LOGGER.error("Не удалось сохранить историю чата {}", path.getFileName(),
					exception);
			return false;
		}
	}

	private Path pathFor(String serverKey) {
		return directory.resolve(serverKey + ".json");
	}
}
