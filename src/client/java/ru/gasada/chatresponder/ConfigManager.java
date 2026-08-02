package ru.gasada.chatresponder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
			.resolve("gasada-chat-responder.json");

	private ConfigManager() {
	}

	public static ServerTemplateRepository templateRepository() {
		return new ServerTemplateRepository(CONFIG_PATH.getParent());
	}

	public static ResponderConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			return ResponderConfig.defaults();
		}
		LegacyConfigToVanillaBoxMigration migration = new LegacyConfigToVanillaBoxMigration(
				CONFIG_PATH, new ServerTemplateRepository(CONFIG_PATH.getParent()));
		TemplateOperationResult<ServerTemplate> migrationResult = migration.migrateIfNeeded();
		if (!migrationResult.success()) {
			GasadaChatResponderClient.LOGGER.warn("[CONFIG] Миграция Vanilla-box не выполнена: {}",
					migrationResult.errorMessage(), migrationResult.error());
		}

		try {
			ResponderConfig config = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), ResponderConfig.class);
			if (config == null) {
				return ResponderConfig.defaults();
			}
			config.sanitize();
			return config;
		} catch (Exception exception) {
			GasadaChatResponderClient.LOGGER.error("Не удалось прочитать настройки автоответчика", exception);
			return ResponderConfig.defaults();
		}
	}

	public static boolean save(ResponderConfig config) {
		config.sanitize();
		String activeId = GasadaChatResponderClient.TEMPLATE_RUNTIME == null ? null
				: GasadaChatResponderClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::id).orElse(null);
		if (activeId != null && !LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID.equals(activeId)) {
			return saveActiveTemplateView(config, activeId);
		}
		Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(temporaryPath, GSON.toJson(config), StandardCharsets.UTF_8);
			try {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
			TemplateOperationResult<ServerTemplate> loadedVanilla = templateRepository().loadTemplate(
					LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID);
			if (!loadedVanilla.success()) {
				GasadaChatResponderClient.LOGGER.error("Не удалось безопасно обновить Vanilla-box: {}",
						loadedVanilla.errorMessage(), loadedVanilla.error());
				return false;
			}
			ServerTemplate vanilla = loadedVanilla.value();
			LegacyConfigToVanillaBoxMigration.applyLegacyFields(vanilla, config);
			if (!templateRepository().saveTemplate(vanilla).success()) {
				return false;
			}
			if (GasadaChatResponderClient.TEMPLATE_RUNTIME != null) {
				GasadaChatResponderClient.TEMPLATE_RUNTIME.switchTo(vanilla);
			}
			return true;
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(temporaryPath);
			} catch (IOException cleanupError) {
				exception.addSuppressed(cleanupError);
			}
			GasadaChatResponderClient.LOGGER.error("Не удалось сохранить настройки автоответчика", exception);
			return false;
		}
	}

	private static boolean saveActiveTemplateView(ResponderConfig config, String activeId) {
		ServerTemplateRepository repository = templateRepository();
		TemplateOperationResult<ServerTemplate> loaded = repository.loadTemplate(activeId);
		if (!loaded.success()) {
			GasadaChatResponderClient.LOGGER.error("Не удалось загрузить активный шаблон: {}", activeId);
			return false;
		}
		ServerTemplate template = loaded.value();
		LegacyConfigToVanillaBoxMigration.applyLegacyFields(template, config);
		TemplateOperationResult<Void> saved = repository.saveTemplate(template);
		if (!saved.success()) {
			GasadaChatResponderClient.LOGGER.error("Не удалось сохранить активный шаблон: {}", activeId,
					saved.error());
			return false;
		}
		GasadaChatResponderClient.TEMPLATE_RUNTIME.switchTo(template);
		return true;
	}

	public static void populateView(ResponderConfig config, ServerTemplate template) {
		LegacyConfigToVanillaBoxMigration.populateLegacyView(config, template);
		config.sanitize();
	}
}
