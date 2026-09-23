package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ThemeManager {
	private static final String DIRECTORY_NAME = "cndl-chat-plus-themes";
	private static final String ACTIVE_FILE = "active.txt";
	private static final List<String> BUNDLED_THEMES = List.of("default.json", "vanilla.json", "midnight.json", "contrast.json", "sakura.json", "nocturne-glass.json");
	private static volatile UiTheme current = UiTheme.defaults();
	private static volatile List<UiTheme.Meta> available = List.of(UiTheme.defaults().meta());
	private static Path directory;

	private ThemeManager() {
	}

	public static void initialize(Path configDirectory) {
		directory = configDirectory.resolve(DIRECTORY_NAME);
		try {
			Files.createDirectories(directory);
			for (String theme : BUNDLED_THEMES) copyBundled(theme);
		} catch (IOException error) {
			CndlChatPlusClient.LOGGER.warn("Не удалось подготовить каталог тем {}", directory, error);
		}
		reload();
		CndlChatPlusClient.LOGGER.info("Темы: {} доступно в {}", available().size(), directory);
	}

	public static UiTheme current() {
		return current;
	}

	public static boolean setTheme(String id) {
		if (directory == null || !validId(id)) {
			current = UiTheme.defaults();
			return false;
		}
		Path file = directory.resolve(id + ".json");
		if (!Files.exists(file)) {
			if (!id.equals(UiTheme.defaults().meta().id())) {
				CndlChatPlusClient.LOGGER.warn("Тема не найдена: {}", file.getFileName());
				return false;
			}
			current = UiTheme.defaults();
			return writeActive(id);
		}
		current = ThemeLoader.load(file, UiTheme.defaults());
		return writeActive(id);
	}

	public static void reload() {
		if (directory == null) return;
		List<UiTheme> themes = ThemeLoader.scan(directory);
		available = metas(themes);
		String id = readActive();
		if (id == null) id = UiTheme.defaults().meta().id();
		Path file = directory.resolve(id + ".json");
		if (!Files.exists(file)) {
			if (!id.equals(UiTheme.defaults().meta().id())) {
				try {
					Files.deleteIfExists(directory.resolve(ACTIVE_FILE));
				} catch (IOException error) {
					CndlChatPlusClient.LOGGER.warn("Не удалось удалить выбор отсутствующей темы", error);
				}
			}
			current = UiTheme.defaults();
			return;
		}
		current = ThemeLoader.load(file, UiTheme.defaults());
	}

	public static List<UiTheme.Meta> available() {
		return available;
	}

	public static Path themesDirectory() {
		return directory;
	}

	private static void copyBundled(String name) throws IOException {
		Path target = directory.resolve(name);
		if (Files.exists(target)) return;
		try (InputStream input = ThemeManager.class.getResourceAsStream("/assets/cndl_chat_plus/themes/" + name)) {
			if (input == null) throw new IOException("Bundled theme missing: " + name);
			Files.copy(input, target);
		}
	}

	private static List<UiTheme.Meta> metas(List<UiTheme> themes) {
		Map<String, UiTheme.Meta> byId = new LinkedHashMap<>();
		byId.put(UiTheme.defaults().meta().id(), UiTheme.defaults().meta());
		themes.stream().sorted(Comparator.comparing(theme -> theme.meta().id())).forEach(theme ->
				byId.putIfAbsent(theme.meta().id(), theme.meta()));
		return List.copyOf(new ArrayList<>(byId.values()));
	}

	private static String readActive() {
		try {
			String id = Files.readString(directory.resolve(ACTIVE_FILE), StandardCharsets.UTF_8).trim();
			return validId(id) ? id : null;
		} catch (IOException error) {
			return null;
		}
	}

	private static boolean writeActive(String id) {
		Path active = directory.resolve(ACTIVE_FILE);
		Path temporary = active.resolveSibling(ACTIVE_FILE + ".tmp");
		try {
			Files.writeString(temporary, id, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, active, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, active, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException error) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException cleanupError) {
				error.addSuppressed(cleanupError);
			}
			CndlChatPlusClient.LOGGER.warn("Не удалось сохранить выбранную тему", error);
			return false;
		}
	}

	private static boolean validId(String id) {
		return id != null && !id.isBlank() && id.matches("[A-Za-z0-9_-]+");
	}
}
