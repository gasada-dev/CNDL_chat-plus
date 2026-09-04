package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
			.resolve("cndl-chat-plus.json");

	private ConfigManager() {
	}

	public static ServerTemplateRepository templateRepository() {
		return new ServerTemplateRepository(CONFIG_PATH.getParent());
	}

	public static Path templateImportDirectory() {
		return CONFIG_PATH.getParent().resolve("cndl-chat-plus-template-imports");
	}

	public static Path chatHistoryDirectory() {
		return CONFIG_PATH.getParent().resolve("cndl-chat-plus-chat-history");
	}

	public static Path chatBookmarksDirectory() {
		return CONFIG_PATH.getParent().resolve("cndl-chat-plus-chat-bookmarks");
	}

	public static ResponderConfig load() {
		try {
			BrandPathMigration.migrate(CONFIG_PATH.getParent());
		} catch (IOException error) {
			CndlChatPlusClient.LOGGER.error("Не удалось перенести legacy-файлы CNDL_chat+", error);
		}
		if (!Files.exists(CONFIG_PATH)) {
			return ResponderConfig.defaults();
		}
		LegacyConfigToVanillaBoxMigration migration = new LegacyConfigToVanillaBoxMigration(
				CONFIG_PATH, new ServerTemplateRepository(CONFIG_PATH.getParent()));
		TemplateOperationResult<ServerTemplate> migrationResult = migration.migrateIfNeeded();
		if (!migrationResult.success()) {
			CndlChatPlusClient.LOGGER.warn("[CONFIG] Миграция Vanilla-box не выполнена: {}",
					migrationResult.errorMessage(), migrationResult.error());
		}

		try {
			String serialized = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
			ResponderConfig config = ResponderConfigJson.read(GSON, serialized);
			if (config == null) {
				return ResponderConfig.defaults();
			}
			JsonObject source = GSON.fromJson(serialized, JsonObject.class);
			if (source != null && !source.has("friendSoundEnabled")) {
				config.friendSoundEnabled = defaultTemplateFriendSound();
				if (!writeConfig(config)) {
					CndlChatPlusClient.LOGGER.warn("Не удалось сохранить перенос глобальной настройки звука друзей");
				}
			}
			config.sanitize();
			return config;
		} catch (Exception exception) {
			CndlChatPlusClient.LOGGER.error("Не удалось прочитать настройки CNDL_chat+", exception);
			return ResponderConfig.defaults();
		}
	}

	private static boolean defaultTemplateFriendSound() {
		ServerTemplateRepository repository = templateRepository();
		TemplateOperationResult<RootConfig> root = repository.loadRoot();
		if (!root.success() || root.value().defaultTemplateId == null) return true;
		TemplateOperationResult<ServerTemplate> template = repository.loadTemplate(root.value().defaultTemplateId);
		return !template.success() || template.value().friendSoundEnabled;
	}

	public static boolean save(ResponderConfig config) {
		return save(config, true);
	}

	public static boolean saveGlobalSettings(ResponderConfig config) {
		config.sanitize();
		ResponderConfig persisted;
		try {
			persisted = Files.exists(CONFIG_PATH)
					? ResponderConfigJson.read(GSON, Files.readString(CONFIG_PATH, StandardCharsets.UTF_8))
					: ResponderConfig.defaults();
			if (persisted == null) {
				CndlChatPlusClient.LOGGER.error("Не удалось сохранить глобальные настройки: пустой config");
				return false;
			}
		} catch (Exception exception) {
			CndlChatPlusClient.LOGGER.error("Не удалось прочитать config перед сохранением глобальных настроек",
					exception);
			return false;
		}
		persisted.applyGlobalSettingsFrom(config);
		return writeConfig(persisted);
	}

	static boolean saveVanillaBoxLastSeen(ResponderConfig config) {
		return save(config, false);
	}

	private static boolean save(ResponderConfig config, boolean reloadRuntime) {
		config.sanitize();
		String activeId = CndlChatPlusClient.TEMPLATE_RUNTIME == null ? null
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::id).orElse(null);
		if (activeId != null && !LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID.equals(activeId)) {
			return saveActiveTemplateView(config, activeId);
		}
		if (!writeConfig(config)) {
			return false;
		}

		try {
			TemplateOperationResult<ServerTemplate> loadedVanilla = templateRepository().loadTemplate(
					LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID);
			if (!loadedVanilla.success()) {
				CndlChatPlusClient.LOGGER.error("Не удалось безопасно обновить Vanilla-box: {}",
						loadedVanilla.errorMessage(), loadedVanilla.error());
				return false;
			}
			ServerTemplate vanilla = loadedVanilla.value();
			LegacyConfigToVanillaBoxMigration.applyVisibleFields(vanilla, config);
			if (!templateRepository().saveTemplate(vanilla).success()) {
				return false;
			}
			if (reloadRuntime && CndlChatPlusClient.TEMPLATE_RUNTIME != null) {
				CndlChatPlusClient.TEMPLATE_RUNTIME.switchTo(vanilla);
			}
			return true;
		} catch (RuntimeException exception) {
			CndlChatPlusClient.LOGGER.error("Не удалось сохранить настройки CNDL_chat+", exception);
			return false;
		}
	}

	private static boolean writeConfig(ResponderConfig config) {
		Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(temporaryPath, GSON.toJson(config), StandardCharsets.UTF_8);
			try {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(temporaryPath);
			} catch (IOException cleanupError) {
				exception.addSuppressed(cleanupError);
			}
			CndlChatPlusClient.LOGGER.error("Не удалось записать настройки CNDL_chat+", exception);
			return false;
		}
	}

	private static boolean saveActiveTemplateView(ResponderConfig config, String activeId) {
		ServerTemplateRepository repository = templateRepository();
		TemplateOperationResult<ServerTemplate> loaded = repository.loadTemplate(activeId);
		if (!loaded.success()) {
			CndlChatPlusClient.LOGGER.error("Не удалось загрузить активный шаблон: {}", activeId);
			return false;
		}
		ServerTemplate template = loaded.value();
		LegacyConfigToVanillaBoxMigration.applyVisibleFields(template, config);
		TemplateOperationResult<Void> saved = repository.saveTemplate(template);
		if (!saved.success()) {
			CndlChatPlusClient.LOGGER.error("Не удалось сохранить активный шаблон: {}", activeId,
					saved.error());
			return false;
		}
		CndlChatPlusClient.TEMPLATE_RUNTIME.switchTo(template);
		return true;
	}
}
