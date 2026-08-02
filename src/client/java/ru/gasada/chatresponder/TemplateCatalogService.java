package ru.gasada.chatresponder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;

public final class TemplateCatalogService {
	static final String BUNDLED_INDEX = "assets/gasada_chat_responder/server_templates/index.txt";
	static final int MAX_TEMPLATE_BYTES = 1_048_576;
	private static final String BUNDLED_DIRECTORY = "assets/gasada_chat_responder/server_templates/";
	private static final Gson GSON = new Gson();

	private final ServerTemplateRepository repository;
	private final ServerTemplateManager manager;
	private final Path importDirectory;
	private final ClassLoader resources;

	public TemplateCatalogService(ServerTemplateRepository repository, Path importDirectory) {
		this(repository, importDirectory, TemplateCatalogService.class.getClassLoader());
	}

	TemplateCatalogService(ServerTemplateRepository repository, Path importDirectory, ClassLoader resources) {
		this.repository = repository;
		this.manager = new ServerTemplateManager(repository);
		this.importDirectory = importDirectory;
		this.resources = resources;
	}

	public ImportSummary installBundledTemplates() {
		try (InputStream index = resources.getResourceAsStream(BUNDLED_INDEX)) {
			if (index == null) return new ImportSummary(0, 0, List.of("Не найден индекс встроенных шаблонов"));
			String content = decode(index.readNBytes(MAX_TEMPLATE_BYTES + 1));
			if (content == null) return new ImportSummary(0, 0, List.of("Некорректный индекс шаблонов"));
			List<TemplateSource> sources = content.lines().map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.map(file -> new TemplateSource(file, () -> resources.getResourceAsStream(BUNDLED_DIRECTORY + file)))
					.toList();
			return importSources(sources);
		} catch (IOException error) {
			return new ImportSummary(0, 0, List.of("Не удалось прочитать встроенные шаблоны: " + error.getMessage()));
		}
	}

	public ImportSummary importUserTemplates() {
		try {
			Files.createDirectories(importDirectory);
			List<TemplateSource> sources = new ArrayList<>();
			try (var files = Files.list(importDirectory)) {
				files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
						.sorted(Comparator.comparing(path -> path.getFileName().toString()))
						.forEach(path -> sources.add(new TemplateSource(path.getFileName().toString(),
								() -> Files.newInputStream(path))));
			}
			return importSources(sources);
		} catch (IOException error) {
			return new ImportSummary(0, 0, List.of("Не удалось открыть папку шаблонов: " + error.getMessage()));
		}
	}

	public Path importDirectory() {
		return importDirectory;
	}

	private ImportSummary importSources(List<TemplateSource> sources) {
		int installed = 0;
		int skipped = 0;
		List<String> errors = new ArrayList<>();
		for (TemplateSource source : sources) {
			try (InputStream input = source.opener.open()) {
				if (input == null) {
					errors.add(source.name + ": файл не найден");
					continue;
				}
				String json = decode(input.readNBytes(MAX_TEMPLATE_BYTES + 1));
				ServerTemplate template = json == null ? null : GSON.fromJson(json, ServerTemplate.class);
				if (template == null) {
					errors.add(source.name + ": некорректный JSON или размер");
					continue;
				}
				template.sanitize();
				TemplateOperationResult<RootConfig> loadedRoot = repository.loadRoot();
				if (!loadedRoot.success()) {
					errors.add(source.name + ": " + loadedRoot.errorMessage());
					continue;
				}
				if (loadedRoot.value().templates.stream()
						.anyMatch(info -> template.id != null && template.id.equals(info.id))) {
					skipped++;
					continue;
				}
				List<String> validationErrors = TemplateSettingsValidator.validate(template);
				if (!validationErrors.isEmpty()) {
					errors.add(source.name + ": " + String.join("; ", validationErrors));
					continue;
				}
				TemplateOperationResult<ServerTemplate> imported = manager.importNew(template);
				if (imported.success()) installed++;
				else errors.add(source.name + ": " + imported.errorMessage());
			} catch (Exception error) {
				errors.add(source.name + ": " + error.getMessage());
			}
		}
		return new ImportSummary(installed, skipped, List.copyOf(errors));
	}

	private static String decode(byte[] bytes) {
		if (bytes.length > MAX_TEMPLATE_BYTES) return null;
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException error) {
			return null;
		}
	}

	public record ImportSummary(int installed, int skipped, List<String> errors) {
		public boolean success() { return errors.isEmpty(); }
	}

	@FunctionalInterface
	private interface InputOpener { InputStream open() throws IOException; }
	private record TemplateSource(String name, InputOpener opener) { }
}
