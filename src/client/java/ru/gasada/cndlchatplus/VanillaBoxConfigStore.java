package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class VanillaBoxConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private final Path configRoot;
	private final Path path;

	VanillaBoxConfigStore(Path configDirectory) {
		configRoot = configDirectory.toAbsolutePath().normalize();
		path = configRoot.resolve("server-templates").resolve("vanilla-box.json");
	}

	ConfigOperationResult<VanillaBoxConfig> load() {
		try {
			assertNoSymlinkComponents(path);
			if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
				return ConfigOperationResult.failure("Конфигурация Vanilla-box не найдена", null);
			}
			assertRegularFile(path);
			VanillaBoxConfig config;
			try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ,
					LinkOption.NOFOLLOW_LINKS)) {
				config = GSON.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8),
						VanillaBoxConfig.class);
			}
			if (config == null) {
				return ConfigOperationResult.failure("Конфигурация Vanilla-box пуста", null);
			}
			config.sanitize();
			return ConfigOperationResult.success(config);
		} catch (Exception error) {
			return ConfigOperationResult.failure("Не удалось прочитать конфигурацию Vanilla-box", error);
		}
	}

	ConfigOperationResult<Void> save(VanillaBoxConfig config) {
		if (config == null) {
			return ConfigOperationResult.failure("Конфигурация Vanilla-box пуста", null);
		}
		config.sanitize();
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			assertNoSymlinkComponents(path.getParent());
			Files.createDirectories(path.getParent());
			assertNoSymlinkComponents(path.getParent());
			assertRegularFileOrAbsent(path);
			assertRegularFileOrAbsent(temporary);
			Files.deleteIfExists(temporary);
			Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
			assertRegularFileOrAbsent(path);
			assertRegularFile(temporary);
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
			return ConfigOperationResult.success(null);
		} catch (IOException error) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException cleanupError) {
				error.addSuppressed(cleanupError);
			}
			return ConfigOperationResult.failure("Не удалось сохранить конфигурацию Vanilla-box", error);
		}
	}

	private void assertNoSymlinkComponents(Path candidate) throws IOException {
		if (!candidate.startsWith(configRoot)) throw new IOException("Fixed config path выходит за config root");
		if (Files.isSymbolicLink(configRoot)) throw new IOException("Symlink запрещён: config root");
		Path current = configRoot;
		for (Path component : configRoot.relativize(candidate)) {
			current = current.resolve(component);
			if (Files.isSymbolicLink(current)) {
				throw new IOException("Symlink запрещён: " + configRoot.relativize(current));
			}
		}
	}

	private static void assertRegularFileOrAbsent(Path candidate) throws IOException {
		if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) assertRegularFile(candidate);
	}

	private static void assertRegularFile(Path candidate) throws IOException {
		if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate)) {
			throw new IOException("Ожидался обычный файл: " + candidate);
		}
	}
}
