package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final java.util.List<String> GLOBAL_FIELDS = java.util.List.of(
			"discordChatEnabled", "friendHudEnabled", "friendSoundEnabled", "teleportRequestSoundEnabled",
			"chatHistoryEnabled", "chatHistoryPersist", "chatHistoryLimit", "chatTabsEnabled",
			"chatTimestampsEnabled", "chatSearchEnabled", "chatContextMenuEnabled",
			"chatDuplicateCollapseEnabled", "whitenBlackNames", "chatAlertsEnabled", "chatAlertRules",
			"chatBinds");

	private ConfigManager() {
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("cndl-chat-plus.json");
	}

	public static Path chatHistoryDirectory() {
		return configPath().getParent().resolve("cndl-chat-plus-chat-history");
	}

	public static Path chatBookmarksDirectory() {
		return configPath().getParent().resolve("cndl-chat-plus-chat-bookmarks");
	}

	public static ResponderConfig load() {
		return load(configPath()).config();
	}

	static LoadedConfig loadForBootstrap() {
		return load(configPath());
	}

	static LoadedConfig load(Path configPath) {
		try {
			BrandPathMigration.migrate(configPath.getParent());
		} catch (IOException error) {
			CndlChatPlusClient.LOGGER.error("Не удалось перенести legacy-файлы CNDL_chat+", error);
		}
		ConfigOperationResult<VanillaBoxConfig> migrationResult =
				new VanillaBoxStorageMigration(configPath).migrate();
		if (!migrationResult.success()) {
			CndlChatPlusClient.LOGGER.warn("[CONFIG] Миграция Vanilla-box не выполнена: {}",
					migrationResult.errorMessage(), migrationResult.error());
		}

		try {
			String serialized = Files.readString(configPath, StandardCharsets.UTF_8);
			ResponderConfig config = ResponderConfigJson.read(GSON, serialized);
			if (config == null) {
				return new LoadedConfig(ResponderConfig.defaults(), migrationResult);
			}
			JsonObject source = GSON.fromJson(serialized, JsonObject.class);
			if (source != null && !source.has("friendSoundEnabled")) {
				config.friendSoundEnabled = defaultTemplateFriendSound(configPath);
				if (!writeConfig(configPath, GSON.toJsonTree(config))) {
					CndlChatPlusClient.LOGGER.warn("Не удалось сохранить перенос глобальной настройки звука друзей");
				}
			}
			if (migrationResult.success()) {
				VanillaBoxConfig.populateCompatibleView(config, migrationResult.value());
			}
			config.sanitize();
			return new LoadedConfig(config, migrationResult);
		} catch (Exception exception) {
			CndlChatPlusClient.LOGGER.error("Не удалось прочитать настройки CNDL_chat+", exception);
			return new LoadedConfig(ResponderConfig.defaults(),
					ConfigOperationResult.failure("Не удалось загрузить конфигурацию Vanilla-box", exception));
		}
	}

	private static boolean defaultTemplateFriendSound(Path configPath) {
		ConfigOperationResult<VanillaBoxConfig> config = new VanillaBoxConfigStore(configPath.getParent()).load();
		return !config.success() || config.value().friendSoundEnabled;
	}

	public static boolean save(ResponderConfig config) {
		return save(config, configPath(), CndlChatPlusClient.VANILLA_BOX_RUNTIME);
	}

	public static boolean saveGlobalSettings(ResponderConfig config) {
		return saveGlobalSettings(config, configPath());
	}

	static boolean saveGlobalSettings(ResponderConfig config, Path configPath) {
		config.sanitize();
		JsonObject persisted;
		try {
			persisted = Files.exists(configPath)
					? JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8)).getAsJsonObject()
					: GSON.toJsonTree(ResponderConfig.defaults()).getAsJsonObject();
		} catch (Exception exception) {
			CndlChatPlusClient.LOGGER.error("Не удалось прочитать config перед сохранением глобальных настроек",
					exception);
			return false;
		}
		JsonObject source = GSON.toJsonTree(config).getAsJsonObject();
		for (String field : GLOBAL_FIELDS) {
			JsonElement value = source.get(field);
			if (value == null) persisted.remove(field);
			else persisted.add(field, value.deepCopy());
		}
		return writeConfig(configPath, persisted);
	}

	static boolean save(ResponderConfig config, Path configPath, VanillaBoxRuntime runtime) {
		boolean runtimeWasActive = runtime != null && runtime.activeSnapshot().isPresent();
		ConfigOperationResult<VanillaBoxConfig> persisted = persistVisible(config, configPath);
		if (!persisted.success()) return false;
		try {
			if (runtimeWasActive) runtime.activate(persisted.value());
			return true;
		} catch (RuntimeException exception) {
			CndlChatPlusClient.LOGGER.error("Не удалось опубликовать настройки Vanilla-box", exception);
			return false;
		}
	}

	static boolean saveLastSeen(ResponderConfig config, Path configPath, VanillaBoxRuntime runtime,
			String player, String value) {
		ConfigOperationResult<VanillaBoxConfig> persisted = persistLastSeen(config, configPath, player, value);
		if (!persisted.success()) return false;
		return runtime.updateLastSeen(player, value).isPresent();
	}

	static boolean saveLastSeen(ResponderConfig config, VanillaBoxRuntime runtime, String player, String value) {
		return saveLastSeen(config, configPath(), runtime, player, value);
	}

	private static ConfigOperationResult<VanillaBoxConfig> persistLastSeen(ResponderConfig config, Path configPath,
			String player, String value) {
		config.sanitize();
		if (!writeConfig(configPath, GSON.toJsonTree(config))) {
			return ConfigOperationResult.failure("Не удалось сохранить совместимый config", null);
		}
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(configPath.getParent());
		ConfigOperationResult<VanillaBoxConfig> loaded = store.load();
		if (!loaded.success()) return loaded;
		VanillaBoxConfig storedConfig = loaded.value();
		storedConfig.friendLastSeen.keySet().removeIf(key -> key.equalsIgnoreCase(player));
		storedConfig.friendLastSeen.put(player, value);
		ConfigOperationResult<Void> saved = store.save(storedConfig);
		if (!saved.success()) {
			CndlChatPlusClient.LOGGER.error("Не удалось сохранить последнее посещение Vanilla-box: {}",
					saved.errorMessage(), saved.error());
		}
		return saved.success() ? ConfigOperationResult.success(storedConfig)
				: ConfigOperationResult.failure(saved.errorMessage(), saved.error());
	}

	private static ConfigOperationResult<VanillaBoxConfig> persistVisible(ResponderConfig config, Path configPath) {
		config.sanitize();
		if (!writeConfig(configPath, GSON.toJsonTree(config))) {
			return ConfigOperationResult.failure("Не удалось сохранить совместимый config", null);
		}
		try {
			VanillaBoxConfigStore store = new VanillaBoxConfigStore(configPath.getParent());
			ConfigOperationResult<VanillaBoxConfig> loaded = store.load();
			if (!loaded.success()) {
				CndlChatPlusClient.LOGGER.error("Не удалось безопасно обновить Vanilla-box: {}",
						loaded.errorMessage(), loaded.error());
				return loaded;
			}
			VanillaBoxConfig storedConfig = loaded.value();
			VanillaBoxConfig.applyVisibleFields(storedConfig, config);
			ConfigOperationResult<Void> saved = store.save(storedConfig);
			if (!saved.success()) {
				CndlChatPlusClient.LOGGER.error("Не удалось сохранить фиксированную конфигурацию Vanilla-box: {}",
						saved.errorMessage(), saved.error());
				return ConfigOperationResult.failure(saved.errorMessage(), saved.error());
			}
			return ConfigOperationResult.success(storedConfig);
		} catch (RuntimeException exception) {
			CndlChatPlusClient.LOGGER.error("Не удалось сохранить настройки CNDL_chat+", exception);
			return ConfigOperationResult.failure("Не удалось сохранить настройки CNDL_chat+", exception);
		}
	}

	private static boolean writeConfig(ResponderConfig config) {
		return writeConfig(configPath(), GSON.toJsonTree(config));
	}

	private static boolean writeConfig(Path configPath, JsonElement config) {
		Path temporaryPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
		try {
			Files.createDirectories(configPath.getParent());
			Files.writeString(temporaryPath, GSON.toJson(config), StandardCharsets.UTF_8);
			try {
				Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
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

	record LoadedConfig(ResponderConfig config, ConfigOperationResult<VanillaBoxConfig> vanillaBox) { }

}
