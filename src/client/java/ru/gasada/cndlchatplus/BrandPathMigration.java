package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class BrandPathMigration {
	private static final String OLD_CONFIG = "gasada-chat-responder.json";
	private static final String NEW_CONFIG = "cndl-chat-plus.json";
	private static final String OLD_IMPORTS = "gasada-chat-responder-template-imports";
	private static final String NEW_IMPORTS = "cndl-chat-plus-template-imports";
	private static final String OLD_HISTORY = "gasada-chat-responder-chat-history";
	private static final String NEW_HISTORY = "cndl-chat-plus-chat-history";

	private BrandPathMigration() {
	}

	static void migrate(Path configDirectory) throws IOException {
		copyIfMissing(configDirectory.resolve(OLD_CONFIG), configDirectory.resolve(NEW_CONFIG));
		copyJsonFiles(configDirectory.resolve(OLD_IMPORTS), configDirectory.resolve(NEW_IMPORTS));
		copyJsonFiles(configDirectory.resolve(OLD_HISTORY), configDirectory.resolve(NEW_HISTORY));
	}

	private static void copyJsonFiles(Path sourceDirectory, Path targetDirectory) throws IOException {
		if (!Files.isDirectory(sourceDirectory)) return;
		try (var paths = Files.list(sourceDirectory)) {
			for (Path source : paths.toList()) {
				if (Files.isRegularFile(source) && source.getFileName().toString().endsWith(".json")) {
					copyIfMissing(source, targetDirectory.resolve(source.getFileName()));
				}
			}
		}
	}

	private static void copyIfMissing(Path source, Path target) throws IOException {
		if (!Files.isRegularFile(source) || Files.exists(target)) return;
		Files.createDirectories(target.getParent());
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		try {
			Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, target);
			}
			if (Files.mismatch(source, target) != -1) {
				Files.deleteIfExists(target);
				throw new IOException("Проверка скопированного legacy-файла не пройдена: " + source.getFileName());
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
