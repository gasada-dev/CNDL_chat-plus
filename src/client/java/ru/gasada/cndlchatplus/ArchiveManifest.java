package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

record ArchiveManifest(List<Entry> entries) {
	static final String FILE_NAME = "manifest.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static ArchiveManifest fromFiles(List<RetiredServerSupportArchive.ArchivedFile> files) {
		return new ArchiveManifest(files.stream().map(file -> new Entry(
				unixPath(file.relativePath()), file.bytes().length, sha256(file.bytes()))).toList());
	}

	byte[] bytes() {
		return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
	}

	static ArchiveManifest read(byte[] bytes) throws IOException {
		try {
			ArchiveManifest manifest = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), ArchiveManifest.class);
			if (manifest == null || manifest.entries() == null) throw new IOException("Manifest архива повреждён");
			return manifest;
		} catch (RuntimeException error) {
			throw new IOException("Manifest архива повреждён", error);
		}
	}

	void assertExactEntries(Path archiveRoot, String filesDirectory) throws IOException {
		Set<Path> expected = expectedEntries(filesDirectory);
		try (Stream<Path> paths = Files.walk(archiveRoot)) {
			for (Path path : paths.toList()) {
				if (Files.isSymbolicLink(path) || !expected.remove(archiveRoot.relativize(path))) {
					throw new IOException("Archive содержит неожиданную запись: " + path);
				}
			}
		}
		if (!expected.isEmpty()) throw new IOException("Archive содержит не все manifest entries");
	}

	Set<Path> expectedEntries(String filesDirectory) {
		Set<Path> expected = new HashSet<>();
		expected.add(Path.of(""));
		expected.add(Path.of(FILE_NAME));
		expected.add(Path.of(filesDirectory));
		for (Entry entry : entries) {
			Path file = Path.of(filesDirectory).resolve(entry.path());
			expected.add(file);
			for (Path parent = file.getParent(); parent != null; parent = parent.getParent()) {
				expected.add(parent);
			}
		}
		return expected;
	}

	static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException(error);
		}
	}

	static String unixPath(Path path) {
		return path.toString().replace('\\', '/');
	}

	record Entry(String path, long size, String sha256) {}
}
