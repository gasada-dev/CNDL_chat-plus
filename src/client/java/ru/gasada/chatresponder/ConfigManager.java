package ru.gasada.chatresponder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

	public static ResponderConfig load() {
		if (!Files.exists(CONFIG_PATH)) {
			return ResponderConfig.defaults();
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
		Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(temporaryPath, GSON.toJson(config), StandardCharsets.UTF_8);
			try {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignored) {
				Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException exception) {
			GasadaChatResponderClient.LOGGER.error("Не удалось сохранить настройки автоответчика", exception);
			return false;
		}
	}
}
