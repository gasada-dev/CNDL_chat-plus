package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

public final class ChatBookmarkStore {
	public static final int MAX_BOOKMARKS = 5_000;
	public static final int MAX_TEXT_LENGTH = 8_192;
	public static final int MAX_SENDER_LENGTH = 64;
	private static final int MAX_ID_LENGTH = 128;
	private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
	private static final Gson GSON = new Gson();
	private static final Type BOOKMARK_LIST_TYPE = new TypeToken<List<ChatBookmark>>() { }.getType();
	private final Path directory;
	private final LongSupplier clock;
	private final List<ChatBookmark> bookmarks = new ArrayList<>();
	private String serverKey;
	private String scopeLabel = "Текущая сессия";
	private boolean dirty;
	private boolean lastSaveSucceeded = true;

	public ChatBookmarkStore(Path directory) {
		this(directory, System::currentTimeMillis);
	}

	ChatBookmarkStore(Path directory, LongSupplier clock) {
		this.directory = directory;
		this.clock = clock;
	}

	public void connect(String newServerKey, String newScopeLabel) {
		if (serverKey != null && dirty) save();
		serverKey = newServerKey;
		scopeLabel = newScopeLabel == null || newScopeLabel.isBlank() ? "Текущая сессия" : newScopeLabel;
		bookmarks.clear();
		if (serverKey != null) bookmarks.addAll(load(serverKey));
		dirty = false;
	}

	public void disconnect() {
		if (serverKey != null && dirty) save();
		serverKey = null;
		scopeLabel = "Текущая сессия";
		bookmarks.clear();
		dirty = false;
	}

	public ChatBookmark add(ChatTab channel, String sender, String text) {
		String canonical = sanitizeText(text);
		if (canonical.isBlank()) return null;
		ChatBookmark bookmark = new ChatBookmark(UUID.randomUUID().toString(), clock.getAsLong(), null,
				sanitizeChannel(channel == null ? null : channel.name()), sanitizeSender(sender), canonical);
		bookmarks.addFirst(bookmark);
		if (bookmarks.size() > MAX_BOOKMARKS) bookmarks.removeLast();
		saveAfterMutation();
		return bookmark;
	}

	public boolean remove(String id) {
		boolean removed = id != null && bookmarks.removeIf(bookmark -> id.equals(bookmark.id()));
		if (removed) saveAfterMutation();
		return removed;
	}

	public void clear() {
		if (bookmarks.isEmpty()) return;
		bookmarks.clear();
		saveAfterMutation();
	}

	public List<ChatBookmark> snapshot() {
		return List.copyOf(bookmarks);
	}

	public String scopeLabel() {
		return scopeLabel;
	}

	public boolean lastSaveSucceeded() {
		return lastSaveSucceeded;
	}

	private void saveAfterMutation() {
		dirty = true;
		lastSaveSucceeded = serverKey == null || save();
		if (!lastSaveSucceeded) {
			CndlChatPlusClient.LOGGER.warn("Закладки текущего сервера не сохранены");
		}
	}

	private List<ChatBookmark> load(String key) {
		Path path = pathFor(key);
		if (!Files.exists(path)) return List.of();
		try {
			if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("bookmark file is too large");
			List<ChatBookmark> valid = new ArrayList<>();
			Set<String> ids = new HashSet<>();
			boolean malformedEntryLogged = false;
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonReader json = new JsonReader(reader);
				json.beginArray();
				for (int count = 0; count < MAX_BOOKMARKS && json.hasNext(); count++) {
					JsonElement element = JsonParser.parseReader(json);
					if (!element.isJsonObject()) continue;
					try {
						ChatBookmark sanitized = sanitize(GSON.fromJson(element, ChatBookmark.class));
						if (sanitized != null && ids.add(sanitized.id())) valid.add(sanitized);
					} catch (RuntimeException error) {
						if (!malformedEntryLogged) {
							CndlChatPlusClient.LOGGER.warn("Повреждённая запись закладок {} пропущена",
									path.getFileName());
							malformedEntryLogged = true;
						}
					}
				}
			}
			valid.sort(Comparator.comparingLong(ChatBookmark::savedAtMillis).reversed());
			return valid;
		} catch (Exception exception) {
			CndlChatPlusClient.LOGGER.warn("Не удалось прочитать закладки {}: {}",
					path.getFileName(), exception.toString());
			return List.of();
		}
	}

	private boolean save() {
		Path path = pathFor(serverKey);
		Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(directory);
			byte[] serialized = GSON.toJson(bookmarks, BOOKMARK_LIST_TYPE).getBytes(StandardCharsets.UTF_8);
			if (serialized.length > MAX_FILE_BYTES) throw new IOException("bookmark file is too large");
			Files.write(temporaryPath, serialized);
			try {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
			return true;
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(temporaryPath);
			} catch (IOException cleanupError) {
				exception.addSuppressed(cleanupError);
			}
			CndlChatPlusClient.LOGGER.error("Не удалось сохранить закладки {}", path.getFileName(), exception);
			return false;
		}
	}

	private ChatBookmark sanitize(ChatBookmark bookmark) {
		if (bookmark == null || bookmark.id() == null || bookmark.id().isBlank()
				|| bookmark.id().trim().length() > MAX_ID_LENGTH
				|| bookmark.savedAtMillis() <= 0 || bookmark.text() == null || bookmark.text().isBlank()) {
			return null;
		}
		String text = sanitizeText(bookmark.text());
		if (text.isBlank()) return null;
		Long messageTimestamp = bookmark.messageTimestampMillis() != null
				&& bookmark.messageTimestampMillis() > 0 ? bookmark.messageTimestampMillis() : null;
		return new ChatBookmark(bookmark.id().trim(), bookmark.savedAtMillis(), messageTimestamp,
				sanitizeChannel(bookmark.channel()), sanitizeSender(bookmark.sender()), text);
	}

	private static String sanitizeChannel(String channel) {
		try {
			ChatTab tab = ChatTab.valueOf(channel == null ? "" : channel);
			return tab == ChatTab.ALL ? ChatTab.SYSTEM.name() : tab.name();
		} catch (IllegalArgumentException exception) {
			return ChatTab.SYSTEM.name();
		}
	}

	private static String sanitizeSender(String sender) {
		if (sender == null || sender.isBlank()) return null;
		String trimmed = sender.trim();
		return trimmed.length() <= MAX_SENDER_LENGTH ? trimmed : trimmed.substring(0, MAX_SENDER_LENGTH);
	}

	private static String sanitizeText(String text) {
		String value = ChatMessageTextSanitizer.stripSyntheticLabels(text == null ? "" : text).trim();
		return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
	}

	private Path pathFor(String key) {
		return directory.resolve(key + ".json");
	}
}
