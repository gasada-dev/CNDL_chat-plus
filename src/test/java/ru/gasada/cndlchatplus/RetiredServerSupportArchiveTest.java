package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RetiredServerSupportArchiveTest {
	private static final String ARCHIVE_NAME = "cndl-chat-plus-retired-server-support-v1";

	@TempDir
	Path directory;

	@Test
	void archivesRawRetiredFilesWithDeterministicManifestAndPreservesSources() throws IOException {
		byte[] rootBytes = "{\"defaultTemplateId\":\"other\"}\n".getBytes(StandardCharsets.UTF_8);
		byte[] templateBytes = "not normalized json \u0000".getBytes(StandardCharsets.UTF_8);
		byte[] importBytes = { 0, 1, 2, 13, 10, -1 };
		write("server-templates.json", rootBytes);
		write("server-templates/other.json", templateBytes);
		write("cndl-chat-plus-template-imports/nested/raw.bin", importBytes);
		write("server-templates/vanilla-box.json", "vanilla".getBytes(StandardCharsets.UTF_8));
		write("cndl-chat-plus-chat-history/server.json", "history".getBytes(StandardCharsets.UTF_8));
		write("cndl-chat-plus-chat-bookmarks/server.json", "bookmark".getBytes(StandardCharsets.UTF_8));
		write("gasada-chat-responder-template-imports/source.json", "branded".getBytes(StandardCharsets.UTF_8));
		Set<Path> candidates = Set.of(
				Path.of("server-templates/other.json"),
				Path.of("cndl-chat-plus-template-imports/nested/raw.bin"),
				Path.of("server-templates.json"));

		ConfigOperationResult<List<Path>> result =
				new RetiredServerSupportArchive(directory, candidates).archive();

		assertTrue(result.success(), result.errorMessage());
		assertEquals(List.of(
				Path.of("cndl-chat-plus-template-imports/nested/raw.bin"),
				Path.of("server-templates.json"),
				Path.of("server-templates/other.json")), result.value());
		assertArchivedBytes("server-templates.json", rootBytes);
		assertArchivedBytes("server-templates/other.json", templateBytes);
		assertArchivedBytes("cndl-chat-plus-template-imports/nested/raw.bin", importBytes);
		assertArrayEquals(rootBytes, Files.readAllBytes(directory.resolve("server-templates.json")));
		assertArrayEquals(templateBytes, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertArrayEquals(importBytes,
				Files.readAllBytes(directory.resolve("cndl-chat-plus-template-imports/nested/raw.bin")));
		assertProtectedFile("server-templates/vanilla-box.json", "vanilla");
		assertProtectedFile("cndl-chat-plus-chat-history/server.json", "history");
		assertProtectedFile("cndl-chat-plus-chat-bookmarks/server.json", "bookmark");
		assertProtectedFile("gasada-chat-responder-template-imports/source.json", "branded");
		assertManifest(candidates);
		assertNoTemporaryFiles();
	}

	@Test
	void resumesWhenFixedArchiveExactlyMatchesSources() throws IOException {
		byte[] bytes = "retired".getBytes(StandardCharsets.UTF_8);
		write("server-templates/other.json", bytes);
		Set<Path> candidates = Set.of(Path.of("server-templates/other.json"));
		RetiredServerSupportArchive archive = new RetiredServerSupportArchive(directory, candidates);
		assertTrue(archive.archive().success());

		ConfigOperationResult<List<Path>> resumed = archive.archive();

		assertTrue(resumed.success(), resumed.errorMessage());
		assertEquals(List.copyOf(candidates), resumed.value());
		assertArchivedBytes("server-templates/other.json", bytes);
		assertNoTemporaryFiles();
	}

	@Test
	void resumesMatchingPartialArchiveWithoutOverwritingExistingFile() throws IOException {
		byte[] first = "first".getBytes(StandardCharsets.UTF_8);
		byte[] second = "second".getBytes(StandardCharsets.UTF_8);
		write("server-templates/alpha.json", first);
		write("server-templates/beta.json", second);
		Path existing = archived("server-templates/alpha.json");
		Files.createDirectories(existing.getParent());
		Files.write(existing, first);
		FileTime originalTime = FileTime.fromMillis(123_456_789L);
		Files.setLastModifiedTime(existing, originalTime);

		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
				Set.of(Path.of("server-templates/alpha.json"), Path.of("server-templates/beta.json"))).archive();

		assertTrue(result.success(), result.errorMessage());
		assertEquals(originalTime, Files.getLastModifiedTime(existing));
		assertArchivedBytes("server-templates/alpha.json", first);
		assertArchivedBytes("server-templates/beta.json", second);
		assertTrue(Files.isRegularFile(directory.resolve(ARCHIVE_NAME).resolve("manifest.json")));
		assertArrayEquals(first, Files.readAllBytes(directory.resolve("server-templates/alpha.json")));
		assertArrayEquals(second, Files.readAllBytes(directory.resolve("server-templates/beta.json")));
		assertNoTemporaryFiles();
	}

	@Test
	void resumesAfterCrashLeavesExpectedOrphanedTemporaryFile() throws IOException {
		byte[] first = "first".getBytes(StandardCharsets.UTF_8);
		byte[] second = "second".getBytes(StandardCharsets.UTF_8);
		write("server-templates/alpha.json", first);
		write("server-templates/beta.json", second);
		Path temporary = archived("server-templates/alpha.json")
				.resolveSibling("alpha.json.tmp");
		Files.createDirectories(temporary.getParent());
		Files.write(temporary, first);

		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
				Set.of(Path.of("server-templates/alpha.json"), Path.of("server-templates/beta.json"))).archive();

		assertTrue(result.success(), result.errorMessage());
		assertArchivedBytes("server-templates/alpha.json", first);
		assertArchivedBytes("server-templates/beta.json", second);
		assertManifest(Set.of(Path.of("server-templates/alpha.json"), Path.of("server-templates/beta.json")));
		assertNoTemporaryFiles();
	}

	@Test
	void resumesAfterCrashLeavesMatchingManifestTemporaryFile() throws IOException {
		byte[] source = "source".getBytes(StandardCharsets.UTF_8);
		write("server-templates/other.json", source);
		Set<Path> candidates = Set.of(Path.of("server-templates/other.json"));
		RetiredServerSupportArchive archive = new RetiredServerSupportArchive(directory, candidates);
		assertTrue(archive.archive().success());
		Path manifest = directory.resolve(ARCHIVE_NAME).resolve("manifest.json");
		byte[] expectedManifest = Files.readAllBytes(manifest);
		Path temporary = manifest.resolveSibling("manifest.json.tmp");
		Files.move(manifest, temporary);

		ConfigOperationResult<List<Path>> result = archive.archive();

		assertTrue(result.success(), result.errorMessage());
		assertArrayEquals(expectedManifest, Files.readAllBytes(manifest));
		assertArrayEquals(source, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertNoTemporaryFiles();
	}

	@Test
	void rejectsMismatchingManifestTemporaryFileWithoutChangingSource() throws IOException {
		byte[] source = "source".getBytes(StandardCharsets.UTF_8);
		byte[] mismatch = "mismatch".getBytes(StandardCharsets.UTF_8);
		write("server-templates/other.json", source);
		Path manifest = directory.resolve(ARCHIVE_NAME).resolve("manifest.json");
		Path temporary = manifest.resolveSibling("manifest.json.tmp");
		Files.createDirectories(temporary.getParent());
		Files.write(temporary, mismatch);

		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
				Set.of(Path.of("server-templates/other.json"))).archive();

		assertFalse(result.success());
		assertArrayEquals(mismatch, Files.readAllBytes(temporary));
		assertFalse(Files.exists(manifest));
		assertFalse(Files.exists(archived("server-templates/other.json")));
		assertArrayEquals(source, Files.readAllBytes(directory.resolve("server-templates/other.json")));
	}

	@Test
	void rejectsUnsafeOrphanedTemporaryEntries() throws IOException {
		for (String kind : List.of("collision", "mismatch", "directory", "symlink", "unexpected")) {
			Path configRoot = directory.resolve(kind);
			byte[] source = "source".getBytes(StandardCharsets.UTF_8);
			Path sourcePath = configRoot.resolve("server-templates/other.json");
			Path target = configRoot.resolve(ARCHIVE_NAME).resolve("files/server-templates/other.json");
			Path temporary = target.resolveSibling(kind.equals("unexpected") ? "unexpected.tmp" : "other.json.tmp");
			Files.createDirectories(sourcePath.getParent());
			Files.write(sourcePath, source);
			Files.createDirectories(temporary.getParent());
			switch (kind) {
				case "collision" -> {
					Files.write(target, source);
					Files.write(temporary, source);
				}
				case "mismatch", "unexpected" -> Files.write(temporary, "mismatch".getBytes(StandardCharsets.UTF_8));
				case "directory" -> Files.createDirectories(temporary);
				case "symlink" -> Files.createSymbolicLink(temporary, sourcePath);
				default -> throw new AssertionError(kind);
			}

			ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(configRoot,
					Set.of(Path.of("server-templates/other.json"))).archive();

			assertFalse(result.success(), kind);
			assertTrue(Files.exists(temporary, java.nio.file.LinkOption.NOFOLLOW_LINKS), kind);
			assertFalse(Files.exists(configRoot.resolve(ARCHIVE_NAME).resolve("manifest.json")), kind);
		}
	}

	@Test
	void rejectsMismatchingFileInPartialArchiveWithoutWritingMissingEntries() throws IOException {
		byte[] first = "first".getBytes(StandardCharsets.UTF_8);
		byte[] second = "second".getBytes(StandardCharsets.UTF_8);
		write("server-templates/alpha.json", first);
		write("server-templates/beta.json", second);
		Path existing = archived("server-templates/alpha.json");
		Files.createDirectories(existing.getParent());
		Files.writeString(existing, "mismatch");

		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
				Set.of(Path.of("server-templates/alpha.json"), Path.of("server-templates/beta.json"))).archive();

		assertFalse(result.success());
		assertEquals("mismatch", Files.readString(existing));
		assertFalse(Files.exists(archived("server-templates/beta.json")));
		assertFalse(Files.exists(directory.resolve(ARCHIVE_NAME).resolve("manifest.json")));
		assertArrayEquals(first, Files.readAllBytes(directory.resolve("server-templates/alpha.json")));
		assertArrayEquals(second, Files.readAllBytes(directory.resolve("server-templates/beta.json")));
		assertNoTemporaryFiles();
	}

	@Test
	void rejectsArchivedByteMismatchWithoutChangingSource() throws IOException {
		byte[] original = "source".getBytes(StandardCharsets.UTF_8);
		write("server-templates/other.json", original);
		Set<Path> candidates = Set.of(Path.of("server-templates/other.json"));
		RetiredServerSupportArchive archive = new RetiredServerSupportArchive(directory, candidates);
		assertTrue(archive.archive().success());
		Files.writeString(archived("server-templates/other.json"), "collision");

		ConfigOperationResult<List<Path>> result = archive.archive();

		assertFalse(result.success());
		assertArrayEquals(original, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertNoTemporaryFiles();
	}

	@Test
	void rejectsSourceDriftAgainstExistingArchive() throws IOException {
		write("server-templates/other.json", "before".getBytes(StandardCharsets.UTF_8));
		Set<Path> candidates = Set.of(Path.of("server-templates/other.json"));
		RetiredServerSupportArchive archive = new RetiredServerSupportArchive(directory, candidates);
		assertTrue(archive.archive().success());
		byte[] changed = "after".getBytes(StandardCharsets.UTF_8);
		write("server-templates/other.json", changed);

		ConfigOperationResult<List<Path>> result = archive.archive();

		assertFalse(result.success());
		assertArrayEquals(changed, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertNoTemporaryFiles();
	}

	@Test
	void rejectsUnexpectedArchiveEntryWithoutChangingSource() throws IOException {
		byte[] original = "source".getBytes(StandardCharsets.UTF_8);
		write("server-templates/other.json", original);
		Set<Path> candidates = Set.of(Path.of("server-templates/other.json"));
		RetiredServerSupportArchive archive = new RetiredServerSupportArchive(directory, candidates);
		assertTrue(archive.archive().success());
		Files.writeString(directory.resolve(ARCHIVE_NAME).resolve("unexpected"), "collision");

		ConfigOperationResult<List<Path>> result = archive.archive();

		assertFalse(result.success());
		assertArrayEquals(original, Files.readAllBytes(directory.resolve("server-templates/other.json")));
	}

	@Test
	void rejectsSymlinkCandidateAndSymlinkAncestorWithoutCreatingArchive() throws IOException {
		Path outside = directory.resolveSibling(directory.getFileName() + "-outside");
		Files.createDirectories(outside);
		Files.writeString(outside.resolve("other.json"), "outside");
		Files.createSymbolicLink(directory.resolve("server-templates"), outside);

		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
				Set.of(Path.of("server-templates/other.json"))).archive();

		assertFalse(result.success());
		assertEquals("outside", Files.readString(outside.resolve("other.json")));
		assertFalse(Files.exists(directory.resolve(ARCHIVE_NAME)));
	}

	@Test
	void rejectsSpecialFileCandidateWithoutCreatingArchive() throws IOException {
		Files.createDirectories(directory.resolve("cndl-chat-plus-template-imports/device"));

		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
				Set.of(Path.of("cndl-chat-plus-template-imports/device"))).archive();

		assertFalse(result.success());
		assertTrue(Files.isDirectory(directory.resolve("cndl-chat-plus-template-imports/device")));
		assertFalse(Files.exists(directory.resolve(ARCHIVE_NAME)));
	}

	@Test
	void forcedArchiveWriteFailurePreservesSourceAndLeavesNoTemporaryFile() throws IOException {
		byte[] original = "source".getBytes(StandardCharsets.UTF_8);
		write("server-templates/other.json", original);
		Files.writeString(directory.resolve(ARCHIVE_NAME), "blocks archive directory");

		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
				Set.of(Path.of("server-templates/other.json"))).archive();

		assertFalse(result.success());
		assertArrayEquals(original, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertNoTemporaryFiles();
	}

	@Test
	void rejectsProtectedAndOutOfRootCandidates() throws IOException {
		List<Path> protectedPaths = List.of(
				Path.of("server-templates/vanilla-box.json"),
				Path.of("cndl-chat-plus-chat-history/server.json"),
				Path.of("cndl-chat-plus-chat-bookmarks/server.json"),
				Path.of("gasada-chat-responder.json"),
				Path.of("gasada-chat-responder-template-imports/source.json"),
				Path.of("cndl-chat-plus.legacy-backup.json"),
				Path.of("../outside.json"),
				directory.resolve("server-templates.json"),
				Path.of(ARCHIVE_NAME + "/manifest.json"));
		for (Path protectedPath : protectedPaths) {
		ConfigOperationResult<List<Path>> result = new RetiredServerSupportArchive(directory,
					Set.of(protectedPath)).archive();
			assertFalse(result.success(), protectedPath.toString());
		}
		assertFalse(Files.exists(directory.resolve(ARCHIVE_NAME)));
	}

	private void assertManifest(Set<Path> candidates) throws IOException {
		JsonObject manifest = JsonParser.parseString(Files.readString(
				directory.resolve(ARCHIVE_NAME).resolve("manifest.json"))).getAsJsonObject();
		JsonArray entries = manifest.getAsJsonArray("entries");
		List<String> sorted = candidates.stream().map(path -> path.toString().replace('\\', '/')).sorted().toList();
		assertEquals(sorted.size(), entries.size());
		for (int index = 0; index < sorted.size(); index++) {
			String relative = sorted.get(index);
			byte[] source = Files.readAllBytes(directory.resolve(relative));
			JsonObject entry = entries.get(index).getAsJsonObject();
			assertEquals(Set.of("path", "size", "sha256"), entry.keySet());
			assertEquals(relative, entry.get("path").getAsString());
			assertEquals(source.length, entry.get("size").getAsLong());
			assertEquals(sha256(source), entry.get("sha256").getAsString());
		}
	}

	private void assertArchivedBytes(String relative, byte[] expected) throws IOException {
		assertArrayEquals(expected, Files.readAllBytes(archived(relative)));
	}

	private void assertProtectedFile(String relative, String expected) throws IOException {
		assertEquals(expected, Files.readString(directory.resolve(relative)));
		assertFalse(Files.exists(archived(relative)));
	}

	private Path archived(String relative) {
		return directory.resolve(ARCHIVE_NAME).resolve("files").resolve(relative);
	}

	private void write(String relative, byte[] bytes) throws IOException {
		Path path = directory.resolve(relative);
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}

	private void assertNoTemporaryFiles() throws IOException {
		try (Stream<Path> paths = Files.walk(directory)) {
			assertTrue(paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException error) {
			throw new AssertionError(error);
		}
	}
}
