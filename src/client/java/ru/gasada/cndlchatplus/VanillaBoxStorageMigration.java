package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class VanillaBoxStorageMigration {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final Path VANILLA_PATH = Path.of("server-templates", "vanilla-box.json");
	private static final Path ROOT_PATH = Path.of("server-templates.json");
	private static final Path IMPORTS_PATH = Path.of("cndl-chat-plus-template-imports");

	private final Path mainConfigPath;
	private final Path configRoot;
	private final DeleteOperation deleteOperation;

	VanillaBoxStorageMigration(Path mainConfigPath) {
		this(mainConfigPath, Files::delete);
	}

	VanillaBoxStorageMigration(Path mainConfigPath, DeleteOperation deleteOperation) {
		this.mainConfigPath = mainConfigPath;
		this.configRoot = mainConfigPath.getParent();
		this.deleteOperation = deleteOperation;
	}

	ConfigOperationResult<VanillaBoxConfig> migrate() {
		try {
			ParsedMain main = readMain();
			VanillaBoxConfigStore store = new VanillaBoxConfigStore(configRoot);
			ConfigOperationResult<VanillaBoxConfig> loaded = store.load();
			if (main.config().storageVersion != null && main.config().storageVersion >= 1) {
				return loaded.success() ? loaded : failure(loaded.errorMessage(), loaded.error());
			}

			Path vanillaPath = configRoot.resolve(VANILLA_PATH);
			VanillaBoxConfig expected;
			if (Files.exists(vanillaPath, LinkOption.NOFOLLOW_LINKS)) {
				if (!loaded.success()) return failure(loaded.errorMessage(), loaded.error());
				expected = loaded.value();
			} else {
				expected = VanillaBoxConfig.fromCompatible(main.config());
			}
			ConfigOperationResult<Void> saved = store.save(expected);
			if (!saved.success()) return failure(saved.errorMessage(), saved.error());
			ConfigOperationResult<VanillaBoxConfig> verified = store.load();
			if (!verified.success() || !GSON.toJson(expected).equals(GSON.toJson(verified.value()))) {
				return failure("Проверка записанного Vanilla-box не пройдена", verified.error());
			}

			ConfigOperationResult<List<Path>> archived = archiveOrResume();
			if (!archived.success()) return failure(archived.errorMessage(), archived.error());
			ConfigOperationResult<Void> deleted = deleteArchivedOriginals(archived.value());
			if (!deleted.success()) return failure(deleted.errorMessage(), deleted.error());

			JsonObject completed = main.raw().deepCopy();
			completed.addProperty("storageVersion", 1);
			writeMain(completed);
			ParsedMain reread = readMain();
			if (reread.config().storageVersion == null || reread.config().storageVersion != 1) {
				return failure("Проверка storageVersion не пройдена", null);
			}
			return ConfigOperationResult.success(verified.value());
		} catch (Exception error) {
			return failure("Миграция хранилища Vanilla-box не выполнена", error);
		}
	}

	private ParsedMain readMain() throws IOException {
		JsonObject raw;
		if (!Files.exists(mainConfigPath, LinkOption.NOFOLLOW_LINKS)) {
			raw = GSON.toJsonTree(ResponderConfig.defaults()).getAsJsonObject();
		} else {
			if (!Files.isRegularFile(mainConfigPath, LinkOption.NOFOLLOW_LINKS)
					|| Files.isSymbolicLink(mainConfigPath)) throw new IOException("Main config не является обычным файлом");
			try {
				raw = JsonParser.parseString(Files.readString(mainConfigPath, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (RuntimeException error) {
				throw new IOException("Main config повреждён", error);
			}
		}
		ResponderConfig config;
		try {
			config = ResponderConfigJson.read(GSON, GSON.toJson(raw));
		} catch (RuntimeException error) {
			throw new IOException("Main config повреждён", error);
		}
		if (config == null) throw new IOException("Main config пуст");
		config.sanitize();
		return new ParsedMain(raw, config);
	}

	private ConfigOperationResult<List<Path>> archiveOrResume() throws IOException {
		Set<Path> current = enumerateCandidates();
		Path manifest = configRoot.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY).resolve("manifest.json");
		if (!Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
			return new RetiredServerSupportArchive(configRoot, current).archive();
		}
		RetiredServerSupportArchive archive = new RetiredServerSupportArchive(configRoot, Set.of());
		ConfigOperationResult<List<Path>> verified = archive.verifyExisting();
		if (!verified.success()) return verified;
		if (!new HashSet<>(verified.value()).containsAll(current)) {
			return ConfigOperationResult.failure("После создания архива появились новые retired-файлы", null);
		}
		return verified;
	}

	private Set<Path> enumerateCandidates() throws IOException {
		Set<Path> candidates = new HashSet<>();
		addRegularIfPresent(ROOT_PATH, candidates);
		Path archiveSourceDirectory = configRoot.resolve("server-templates");
		if (Files.exists(archiveSourceDirectory, LinkOption.NOFOLLOW_LINKS)) {
			assertDirectory(archiveSourceDirectory);
			try (Stream<Path> paths = Files.list(archiveSourceDirectory)) {
				for (Path path : paths.toList()) {
					String name = path.getFileName().toString();
					if (name.endsWith(".json") && !name.equals("vanilla-box.json")) {
						addRequiredRegular(configRoot.relativize(path), candidates);
					}
				}
			}
		}
		Path imports = configRoot.resolve(IMPORTS_PATH);
		if (Files.exists(imports, LinkOption.NOFOLLOW_LINKS)) {
			assertDirectory(imports);
			try (Stream<Path> paths = Files.walk(imports)) {
				for (Path path : paths.skip(1).toList()) {
					if (Files.isSymbolicLink(path)) throw new IOException("Symlink запрещён: " + path);
					if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
					addRequiredRegular(configRoot.relativize(path), candidates);
				}
			}
		}
		return Set.copyOf(candidates);
	}

	private void addRegularIfPresent(Path relative, Set<Path> candidates) throws IOException {
		Path path = configRoot.resolve(relative);
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) addRequiredRegular(relative, candidates);
	}

	private void addRequiredRegular(Path relative, Set<Path> candidates) throws IOException {
		Path path = configRoot.resolve(relative);
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
			throw new IOException("Retired path не является обычным файлом: " + relative);
		}
		candidates.add(relative.normalize());
	}

	private static void assertDirectory(Path path) throws IOException {
		if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
			throw new IOException("Retired path не является обычной директорией: " + path);
		}
	}

	private ConfigOperationResult<Void> deleteArchivedOriginals(List<Path> paths) {
		try {
			Path archivedFiles = configRoot.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY).resolve("files");
			for (Path relative : paths) {
				Path source = configRoot.resolve(relative);
				if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
				if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)
						|| Files.mismatch(source, archivedFiles.resolve(relative)) != -1) {
					return ConfigOperationResult.failure("Original не соответствует проверенному архиву: " + relative, null);
				}
			}
			for (Path relative : paths) {
				Path source = configRoot.resolve(relative);
				if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) deleteOperation.delete(source);
			}
			removeEmptyImportsDirectories();
			return ConfigOperationResult.success(null);
		} catch (IOException error) {
			return ConfigOperationResult.failure("Не удалось удалить архивированные server-support файлы", error);
		}
	}

	private void removeEmptyImportsDirectories() throws IOException {
		Path imports = configRoot.resolve(IMPORTS_PATH);
		if (!Files.exists(imports, LinkOption.NOFOLLOW_LINKS)) return;
		try (Stream<Path> paths = Files.walk(imports)) {
			for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
				try (Stream<Path> children = Files.list(path)) {
					if (children.findAny().isEmpty()) Files.delete(path);
				}
			}
		}
	}

	private void writeMain(JsonObject raw) throws IOException {
		Files.createDirectories(mainConfigPath.getParent());
		Path temporary = mainConfigPath.resolveSibling(mainConfigPath.getFileName() + ".tmp");
		try {
			Files.deleteIfExists(temporary);
			Files.writeString(temporary, GSON.toJson(raw), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, mainConfigPath, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, mainConfigPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static <T> ConfigOperationResult<T> failure(String message, Throwable error) {
		return ConfigOperationResult.failure(message, error);
	}

	@FunctionalInterface
	interface DeleteOperation {
		void delete(Path path) throws IOException;
	}

	private record ParsedMain(JsonObject raw, ResponderConfig config) {}
}
