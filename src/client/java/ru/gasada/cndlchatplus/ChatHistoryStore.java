package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public final class ChatHistoryStore {
	private static final Gson GSON = new Gson();
	private static final Type ENTRY_LIST_TYPE = new TypeToken<List<ChatHistoryEntry>>() {
	}.getType();

	private final Path directory;

	public ChatHistoryStore(Path directory) {
		this.directory = directory;
	}

	public static String fileKey(String normalizedAddress) {
		return normalizedAddress.replaceAll("[^A-Za-z0-9.\\-]", "_");
	}

	public List<ChatHistoryEntry> load(String serverKey) {
		Path path = pathFor(serverKey);
		if (!Files.exists(path)) {
			return List.of();
		}
		try {
			List<ChatHistoryEntry> entries = GSON.fromJson(
					Files.readString(path, StandardCharsets.UTF_8), ENTRY_LIST_TYPE);
			if (entries == null) {
				return List.of();
			}
			entries.removeIf(entry -> entry == null || entry.json() == null || entry.json().isBlank());
			return entries;
		} catch (Exception exception) {
			CndlChatPlusClient.LOGGER.warn("Не удалось прочитать историю чата {}: {}",
					path.getFileName(), exception.toString());
			return List.of();
		}
	}

	public boolean save(String serverKey, List<ChatHistoryEntry> entries) {
		Path path = pathFor(serverKey);
		Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(directory);
			Files.writeString(temporaryPath, GSON.toJson(entries, ENTRY_LIST_TYPE), StandardCharsets.UTF_8);
			try {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
			}
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
