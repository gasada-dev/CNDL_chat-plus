package ru.gasada.cndlchatplus;

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
	static final String BUNDLED_CATALOG = "assets/cndl_chat_plus/server_templates/catalog.json";
	static final int MAX_TEMPLATE_BYTES = 1_048_576;
	private static final String BUNDLED_DIRECTORY = "assets/cndl_chat_plus/server_templates/";
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
		try (InputStream index = resources.getResourceAsStream(BUNDLED_CATALOG)) {
			if (index == null) return new ImportSummary(0, 0, List.of("Не найден каталог встроенных шаблонов"));
			String content = decode(index.readNBytes(MAX_TEMPLATE_BYTES + 1));
			BundledDescriptor[] descriptors = content == null ? null
					: GSON.fromJson(content, BundledDescriptor[].class);
			if (descriptors == null) return new ImportSummary(0, 0, List.of("Некорректный каталог шаблонов"));
			List<TemplateSource> sources = java.util.Arrays.stream(descriptors)
					.filter(value -> value != null && value.resource != null && !value.resource.isBlank())
					.map(value -> new TemplateSource(value.resource,
							() -> resources.getResourceAsStream(BUNDLED_DIRECTORY + value.resource),
							value.addressPatterns == null ? List.of() : List.copyOf(value.addressPatterns)))
					.toList();
			return importSources(sources);
		} catch (Exception error) {
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
								() -> Files.newInputStream(path), List.of())));
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
				ServerTemplateInfo existing = loadedRoot.value().templates.stream()
						.filter(info -> template.id != null && template.id.equals(info.id)).findFirst().orElse(null);
				if (existing != null) {
					String mergeError = mergeAddressPatterns(loadedRoot.value(), existing, source.addressPatterns);
					if (mergeError != null) errors.add(source.name + ": " + mergeError);
					String defaultsError = upgradeBundledDefaults(template.id);
					if (defaultsError != null) errors.add(source.name + ": " + defaultsError);
					skipped++;
					continue;
				}
				List<String> validationErrors = TemplateSettingsValidator.validate(template);
				if (!validationErrors.isEmpty()) {
					errors.add(source.name + ": " + String.join("; ", validationErrors));
					continue;
				}
				TemplateOperationResult<ServerTemplate> imported = manager.importNew(template);
				if (imported.success()) {
					installed++;
					TemplateOperationResult<RootConfig> refreshed = repository.loadRoot();
					ServerTemplateInfo info = refreshed.success() ? refreshed.value().templates.stream()
							.filter(value -> template.id.equals(value.id)).findFirst().orElse(null) : null;
					if (info == null) {
						errors.add(source.name + ": не удалось зарегистрировать address patterns");
					} else {
						String mergeError = mergeAddressPatterns(refreshed.value(), info, source.addressPatterns);
						if (mergeError != null) errors.add(source.name + ": " + mergeError);
					}
				}
				else errors.add(source.name + ": " + imported.errorMessage());
			} catch (Exception error) {
				errors.add(source.name + ": " + error.getMessage());
			}
		}
		return new ImportSummary(installed, skipped, List.copyOf(errors));
	}

	private String upgradeBundledDefaults(String templateId) {
		if (!"vanilla-game".equals(templateId) && !"vanilla-box".equals(templateId)) return null;
		TemplateOperationResult<ServerTemplate> loaded = repository.loadTemplate(templateId);
		if (!loaded.success()) return loaded.errorMessage();
		ServerTemplate template = loaded.value();
		boolean changed = false;
		if ("vanilla-box".equals(templateId)) {
			if (!template.commands.nearbyPlayerCommandsConfigured) {
				ServerCommandSettings defaults = ServerCommandSettings.vanillaBoxDefaults();
				if (template.commands.protectionAdd == null || template.commands.protectionAdd.isBlank()) {
					template.commands.protectionAdd = defaults.protectionAdd;
				}
				if (template.commands.protectionRemove == null || template.commands.protectionRemove.isBlank()) {
					template.commands.protectionRemove = defaults.protectionRemove;
				}
				if (template.commands.traderTrustedAdd == null || template.commands.traderTrustedAdd.isBlank()) {
					template.commands.traderTrustedAdd = defaults.traderTrustedAdd;
				}
				template.commands.nearbyPlayerCommandsConfigured = true;
				changed = true;
			}
			if (!template.commands.traderTrustedRemoveConfigured) {
				if (template.commands.traderTrustedRemove == null
						|| template.commands.traderTrustedRemove.isBlank()) {
					template.commands.traderTrustedRemove = ServerCommandSettings.vanillaBoxDefaults()
							.traderTrustedRemove;
				}
				template.commands.traderTrustedRemoveConfigured = true;
				changed = true;
			}
			if (template.commands.marriageList != null && !template.commands.marriageList.isBlank()
					|| template.parsers.marriageEntryPattern != null && !template.parsers.marriageEntryPattern.isBlank()
					|| template.parsers.marriagePagePattern != null && !template.parsers.marriagePagePattern.isBlank()
					|| template.parsers.marriageEmptyPattern != null && !template.parsers.marriageEmptyPattern.isBlank()
					|| template.playerInfo.marriageLookupConfigured) {
				template.commands.marriageList = "";
				template.parsers.marriageEntryPattern = "";
				template.parsers.marriagePagePattern = "";
				template.parsers.marriageEmptyPattern = "";
				template.playerInfo.marriageLookupConfigured = false;
				changed = true;
			}
		}
		if ("vanilla-game".equals(templateId) && !template.playerInfo.providerConfigured) {
			template.playerInfo.provider = PlayerInfoProvider.VANILLA_GAME_PUBLIC_API;
			template.playerInfo.providerConfigured = true;
			changed = true;
		}
		if (!template.parsers.teleportRequestConfigured) {
			template.commands.acceptTeleport = "tpaccept";
			ParserSettings.applyTeleportDefaults(template.parsers);
			changed = true;
		}
		if (!template.parsers.playerInfoPatternsConfigured) {
			ParserSettings defaults = ParserSettings.vanillaBoxDefaults();
			for (var entry : defaults.playerInfoPatterns.entrySet()) {
				template.parsers.playerInfoPatterns.putIfAbsent(entry.getKey(), entry.getValue());
			}
			template.parsers.playerInfoPatternsConfigured = true;
			changed = true;
		}
		if ("vanilla-game".equals(templateId) && !template.playerInfo.marriageLookupConfigured) {
			if (template.commands.marriageList == null || template.commands.marriageList.isBlank()) {
				template.commands.marriageList = "marry list {page}";
			}
			ParserSettings defaults = new ParserSettings();
			ParserSettings.applyVanillaGameMarriageDefaults(defaults);
			if (template.parsers.marriageEntryPattern == null || template.parsers.marriageEntryPattern.isBlank()) {
				template.parsers.marriageEntryPattern = defaults.marriageEntryPattern;
			}
			if (template.parsers.marriagePagePattern == null || template.parsers.marriagePagePattern.isBlank()) {
				template.parsers.marriagePagePattern = defaults.marriagePagePattern;
			}
			if (template.parsers.marriageEmptyPattern == null || template.parsers.marriageEmptyPattern.isBlank()) {
				template.parsers.marriageEmptyPattern = defaults.marriageEmptyPattern;
			}
			template.playerInfo.marriageLookupConfigured = true;
			changed = true;
		}
		if (!changed) return null;
		TemplateOperationResult<Void> saved = repository.saveTemplate(template);
		return saved.success() ? null : saved.errorMessage();
	}

	private String mergeAddressPatterns(RootConfig root, ServerTemplateInfo target, List<String> patterns) {
		if (target.addressPatterns == null) target.addressPatterns = new ArrayList<>();
		boolean changed = false;
		for (String source : patterns) {
			AddressPatternValidator.ValidationResult validated = AddressPatternValidator.validate(source);
			if (!validated.valid()) return validated.errorMessage();
			String pattern = validated.normalizedPattern();
			ServerTemplateInfo owner = root.templates.stream()
					.filter(info -> info.addressPatterns != null && info.addressPatterns.stream()
							.anyMatch(value -> value.equalsIgnoreCase(pattern)))
					.findFirst().orElse(null);
			if (owner != null && !owner.id.equals(target.id)) {
				return "домен " + pattern + " уже принадлежит шаблону " + owner.id;
			}
			if (target.addressPatterns.stream().noneMatch(value -> value.equalsIgnoreCase(pattern))) {
				target.addressPatterns.add(pattern);
				changed = true;
			}
		}
		if (!changed) return null;
		TemplateOperationResult<Void> saved = repository.saveRoot(root);
		return saved.success() ? null : saved.errorMessage();
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
	private record TemplateSource(String name, InputOpener opener, List<String> addressPatterns) { }
	private static final class BundledDescriptor {
		String resource;
		List<String> addressPatterns;
	}
}
