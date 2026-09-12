package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

final class RetiredServerSupportArchive {
	static final String ARCHIVE_DIRECTORY = "cndl-chat-plus-retired-server-support-v1";
	private static final String FILES_DIRECTORY = "files";
	private static final String IMPORTS_DIRECTORY = "cndl-chat-plus-template-imports";
	private static final OpenOption[] READ_NOFOLLOW = {
			StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS
	};

	private final Path configRoot;
	private final Set<Path> candidates;
	private final Path archiveRoot;

	RetiredServerSupportArchive(Path configRoot, Set<Path> candidates) {
		this.configRoot = configRoot.toAbsolutePath().normalize();
		this.candidates = Set.copyOf(candidates);
		this.archiveRoot = this.configRoot.resolve(ARCHIVE_DIRECTORY);
	}

	ConfigOperationResult<List<Path>> archive() {
		List<Path> temporaryPaths = new ArrayList<>();
		try {
			List<Path> relativePaths = validatedCandidates();
			List<ArchivedFile> files = readSources(relativePaths);
			ArchiveManifest expected = ArchiveManifest.fromFiles(files);
			Path manifestPath = archiveRoot.resolve(ArchiveManifest.FILE_NAME);
			if (Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
				verifyArchive(expected, files);
				return ConfigOperationResult.success(relativePaths);
			}
			if (Files.exists(archiveRoot, LinkOption.NOFOLLOW_LINKS)) {
				verifyPartialArchive(expected, files);
			} else {
				Files.createDirectories(archiveRoot.resolve(FILES_DIRECTORY));
			}
			for (ArchivedFile file : files) {
				Path target = archiveRoot.resolve(FILES_DIRECTORY).resolve(file.relativePath());
				if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
					writeAtomic(target, file.bytes(), temporaryPaths);
				}
			}
			writeAtomic(manifestPath, expected.bytes(), temporaryPaths);
			verifyArchive(expected, files);
			return ConfigOperationResult.success(relativePaths);
		} catch (Exception error) {
			cleanupTemporaryFiles(temporaryPaths, error);
			return ConfigOperationResult.failure("Не удалось создать проверенный архив старых серверных данных",
					error);
		}
	}

	ConfigOperationResult<List<Path>> verifyExisting() {
		try {
			if (!Files.isDirectory(archiveRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(archiveRoot)) {
				throw new IOException("Fixed archive path занят не директорией");
			}
			ArchiveManifest manifest = ArchiveManifest.read(readRegularFile(
					archiveRoot.resolve(ArchiveManifest.FILE_NAME)));
			List<Path> paths = new ArrayList<>();
			String previous = null;
			for (ArchiveManifest.Entry entry : manifest.entries()) {
				Path relative = Path.of(entry.path()).normalize();
				if (!isAllowed(relative) || !ArchiveManifest.unixPath(relative).equals(entry.path())
						|| previous != null && previous.compareTo(entry.path()) >= 0) {
					throw new IOException("Manifest архива содержит некорректный path");
				}
				byte[] bytes = readRegularFile(archiveRoot.resolve(FILES_DIRECTORY).resolve(relative));
				if (bytes.length != entry.size() || !ArchiveManifest.sha256(bytes).equals(entry.sha256())) {
					throw new IOException("Archive entry не соответствует manifest: " + relative);
				}
				paths.add(relative);
				previous = entry.path();
			}
			manifest.assertExactEntries(archiveRoot, FILES_DIRECTORY);
			return ConfigOperationResult.success(List.copyOf(paths));
		} catch (Exception error) {
			return ConfigOperationResult.failure("Не удалось проверить архив старых серверных данных", error);
		}
	}

	private List<Path> validatedCandidates() throws IOException {
		if (!Files.isDirectory(configRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(configRoot)) {
			throw new IOException("Config root не является обычной директорией");
		}
		List<Path> normalized = candidates.stream().map(Path::normalize).sorted().toList();
		if (new HashSet<>(normalized).size() != candidates.size()) {
			throw new IOException("Candidate paths пересекаются после нормализации");
		}
		for (Path relative : normalized) {
			if (!isAllowed(relative)) {
				throw new IOException("Candidate path не принадлежит retired server support: " + relative);
			}
			Path source = configRoot.resolve(relative).normalize();
			if (!source.startsWith(configRoot) || source.startsWith(archiveRoot)) {
				throw new IOException("Candidate path выходит за config root: " + relative);
			}
			assertNoSymlinkComponents(source);
			if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
				throw new IOException("Candidate не является обычным файлом: " + relative);
			}
		}
		return List.copyOf(normalized);
	}

	private static boolean isAllowed(Path relative) {
		if (relative.isAbsolute() || relative.getNameCount() == 0 || relative.startsWith("..")) {
			return false;
		}
		String unixPath = ArchiveManifest.unixPath(relative);
		if (unixPath.equals("server-templates.json")) {
			return true;
		}
		if (relative.getNameCount() == 2 && relative.startsWith("server-templates")) {
			String fileName = relative.getFileName().toString();
			return fileName.endsWith(".json") && !fileName.equals("vanilla-box.json");
		}
		return relative.getNameCount() > 1 && relative.startsWith(IMPORTS_DIRECTORY);
	}

	private void assertNoSymlinkComponents(Path path) throws IOException {
		Path current = configRoot;
		for (Path component : configRoot.relativize(path)) {
			current = current.resolve(component);
			if (Files.isSymbolicLink(current)) {
				throw new IOException("Symlink запрещён: " + configRoot.relativize(current));
			}
		}
	}

	private List<ArchivedFile> readSources(List<Path> relativePaths) throws IOException {
		List<ArchivedFile> files = new ArrayList<>(relativePaths.size());
		for (Path relative : relativePaths) {
			files.add(new ArchivedFile(relative, readRegularFile(configRoot.resolve(relative))));
		}
		return files;
	}

	private void verifyArchive(ArchiveManifest expected, List<ArchivedFile> sources) throws IOException {
		if (!Files.isDirectory(archiveRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(archiveRoot)) {
			throw new IOException("Fixed archive path занят не директорией");
		}
		expected.assertExactEntries(archiveRoot, FILES_DIRECTORY);
		if (!expected.equals(ArchiveManifest.read(readRegularFile(archiveRoot.resolve(ArchiveManifest.FILE_NAME))))) {
			throw new IOException("Manifest архива не соответствует candidate set");
		}
		for (ArchivedFile source : sources) {
			byte[] currentSource = readRegularFile(configRoot.resolve(source.relativePath()));
			byte[] archived = readRegularFile(
					archiveRoot.resolve(FILES_DIRECTORY).resolve(source.relativePath()));
			if (!Arrays.equals(source.bytes(), currentSource) || !Arrays.equals(currentSource, archived)) {
				throw new IOException("Archived bytes не прошли проверку: " + source.relativePath());
			}
		}
	}

	private void verifyPartialArchive(ArchiveManifest expected, List<ArchivedFile> sources) throws IOException {
		if (!Files.isDirectory(archiveRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(archiveRoot)) {
			throw new IOException("Fixed archive path занят не директорией");
		}
		Set<Path> allowed = expected.expectedEntries(FILES_DIRECTORY);
		allowed.remove(Path.of(ArchiveManifest.FILE_NAME));
		List<Path> orphanedTemporaryFiles = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(archiveRoot)) {
			for (Path path : paths.toList()) {
				Path relative = archiveRoot.relativize(path);
				if (Files.isSymbolicLink(path)) {
					throw new IOException("Partial archive содержит неожиданную запись: " + path);
				}
				if (allowed.contains(relative)) continue;
				if (isExpectedOrphanedTemporary(relative, path, expected, sources)) {
					orphanedTemporaryFiles.add(path);
					continue;
				}
				throw new IOException("Partial archive содержит неожиданную запись: " + path);
			}
		}
		for (ArchivedFile source : sources) {
			Path archivedPath = archiveRoot.resolve(FILES_DIRECTORY).resolve(source.relativePath());
			if (Files.exists(archivedPath, LinkOption.NOFOLLOW_LINKS)
					&& (!Arrays.equals(source.bytes(), readRegularFile(configRoot.resolve(source.relativePath())))
							|| !Arrays.equals(source.bytes(), readRegularFile(archivedPath)))) {
				throw new IOException("Partial archive bytes не прошли проверку: " + source.relativePath());
			}
		}
		for (Path temporary : orphanedTemporaryFiles) {
			Files.delete(temporary);
		}
	}

	private boolean isExpectedOrphanedTemporary(Path relative, Path temporary, ArchiveManifest expected,
			List<ArchivedFile> sources) throws IOException {
		if (!Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS)) return false;
		if (relative.equals(Path.of(ArchiveManifest.FILE_NAME + ".tmp"))) {
			return Arrays.equals(expected.bytes(), readRegularFile(temporary));
		}
		for (ArchivedFile source : sources) {
			Path target = Path.of(FILES_DIRECTORY).resolve(source.relativePath());
			Path expectedTemporary = target.resolveSibling(target.getFileName() + ".tmp");
			if (relative.equals(expectedTemporary)) {
				return !Files.exists(archiveRoot.resolve(target), LinkOption.NOFOLLOW_LINKS)
						&& Arrays.equals(source.bytes(), readRegularFile(temporary));
			}
		}
		return false;
	}

	private static byte[] readRegularFile(Path path) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
			throw new IOException("Ожидался обычный файл: " + path);
		}
		try (InputStream input = Files.newInputStream(path, READ_NOFOLLOW)) {
			return input.readAllBytes();
		}
	}

	private static void writeAtomic(Path target, byte[] bytes, List<Path> temporaryPaths) throws IOException {
		Files.createDirectories(target.getParent());
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Archive entry collision: " + target);
		}
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		temporaryPaths.add(temporary);
		Files.write(temporary, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(temporary, target);
		}
	}

	private static void cleanupTemporaryFiles(List<Path> temporaryPaths, Exception error) {
		for (Path temporary : temporaryPaths) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException cleanupError) {
				error.addSuppressed(cleanupError);
			}
		}
	}

	static record ArchivedFile(Path relativePath, byte[] bytes) {}
}
