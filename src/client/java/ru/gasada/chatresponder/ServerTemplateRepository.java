package ru.gasada.chatresponder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ServerTemplateRepository {
	public static final String ROOT_FILE_NAME = "server-templates.json";
	private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path directory;
	private final Path templatesDirectory;

	public ServerTemplateRepository(Path directory) {
		this.directory = directory;
		this.templatesDirectory = directory.resolve("server-templates");
	}

	public TemplateOperationResult<RootConfig> loadRoot() {
		Path path = directory.resolve(ROOT_FILE_NAME);
		if (!Files.exists(path)) {
			return TemplateOperationResult.success(new RootConfig());
		}
		try {
			RootConfig root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), RootConfig.class);
			if (root == null) {
				return TemplateOperationResult.failure("Корневая конфигурация шаблонов пуста", null);
			}
			root.sanitize();
			return TemplateOperationResult.success(root);
		} catch (Exception error) {
			return TemplateOperationResult.failure("Не удалось прочитать корневую конфигурацию шаблонов", error);
		}
	}

	public TemplateOperationResult<Void> saveRoot(RootConfig root) {
		if (root == null) {
			return TemplateOperationResult.failure("Корневая конфигурация не задана", null);
		}
		root.sanitize();
		return writeAtomic(directory.resolve(ROOT_FILE_NAME), GSON.toJson(root));
	}

	public TemplateOperationResult<ServerTemplate> loadTemplate(String id) {
		if (!isSafeId(id)) {
			return TemplateOperationResult.failure("Некорректный ID шаблона", null);
		}
		Path path = templatePath(id);
		if (!Files.exists(path)) {
			return TemplateOperationResult.failure("Шаблон не найден: " + id, null);
		}
		try {
			ServerTemplate template = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
					ServerTemplate.class);
			if (template == null || !id.equals(template.id)) {
				return TemplateOperationResult.failure("Файл шаблона не соответствует ID: " + id, null);
			}
			return TemplateOperationResult.success(template);
		} catch (Exception error) {
			return TemplateOperationResult.failure("Не удалось прочитать шаблон: " + id, error);
		}
	}

	public TemplateOperationResult<Void> saveTemplate(ServerTemplate template) {
		if (template == null || !isSafeId(template.id)) {
			return TemplateOperationResult.failure("Некорректный ID шаблона", null);
		}
		return writeAtomic(templatePath(template.id), GSON.toJson(template));
	}

	private TemplateOperationResult<Void> writeAtomic(Path path, String json) {
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			Files.deleteIfExists(temporary);
			Files.writeString(temporary, json, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
			return TemplateOperationResult.success(null);
		} catch (IOException error) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException cleanupError) {
				error.addSuppressed(cleanupError);
			}
			return TemplateOperationResult.failure("Не удалось сохранить " + path.getFileName(), error);
		}
	}

	private Path templatePath(String id) {
		return templatesDirectory.resolve(id + ".json");
	}

	private static boolean isSafeId(String id) {
		return id != null && SAFE_ID.matcher(id).matches();
	}
}
