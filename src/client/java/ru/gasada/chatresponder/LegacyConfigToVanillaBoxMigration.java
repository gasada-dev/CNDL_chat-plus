package ru.gasada.chatresponder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class LegacyConfigToVanillaBoxMigration {
	public static final String VANILLA_BOX_ID = "vanilla-box";
	public static final String VANILLA_BOX_NAME = "Vanilla-box";
	public static final String BACKUP_FILE_NAME = "gasada-chat-responder.legacy-backup.json";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path legacyConfigPath;
	private final ServerTemplateRepository repository;

	public LegacyConfigToVanillaBoxMigration(Path legacyConfigPath, ServerTemplateRepository repository) {
		this.legacyConfigPath = legacyConfigPath;
		this.repository = repository;
	}

	public TemplateOperationResult<ServerTemplate> migrateIfNeeded() {
		TemplateOperationResult<RootConfig> loadedRoot = repository.loadRoot();
		if (!loadedRoot.success()) {
			return TemplateOperationResult.failure(loadedRoot.errorMessage(), loadedRoot.error());
		}
		RootConfig root = loadedRoot.value();
		if (isCompleted(root)) {
			return repository.loadTemplate(VANILLA_BOX_ID);
		}
		if (!Files.exists(legacyConfigPath)) {
			return TemplateOperationResult.failure("Старый config для миграции не найден", null);
		}

		TemplateOperationResult<Void> backup = createAndVerifyBackup();
		if (!backup.success()) {
			return TemplateOperationResult.failure(backup.errorMessage(), backup.error());
		}

		ResponderConfig legacy;
		try {
			legacy = GSON.fromJson(Files.readString(legacyConfigPath, StandardCharsets.UTF_8), ResponderConfig.class);
			if (legacy == null) {
				return TemplateOperationResult.failure("Старый config содержит JSON null", null);
			}
			legacy.sanitize();
		} catch (Exception error) {
			return TemplateOperationResult.failure("Старый config повреждён; миграция не выполнена", error);
		}

		ServerTemplate vanillaBox = fromLegacy(legacy);
		TemplateOperationResult<Void> savedTemplate = repository.saveTemplate(vanillaBox);
		if (!savedTemplate.success()) {
			return TemplateOperationResult.failure(savedTemplate.errorMessage(), savedTemplate.error());
		}
		TemplateOperationResult<ServerTemplate> verifiedTemplate = repository.loadTemplate(VANILLA_BOX_ID);
		if (!verifiedTemplate.success() || !sameServerData(vanillaBox, verifiedTemplate.value())) {
			return TemplateOperationResult.failure("Проверка записанного Vanilla-box не пройдена",
					verifiedTemplate.error());
		}

		root.templates.removeIf(info -> VANILLA_BOX_ID.equals(info.id));
		root.templates.add(new ServerTemplateInfo(VANILLA_BOX_ID, VANILLA_BOX_NAME));
		if (root.defaultTemplateId == null || root.defaultTemplateId.isBlank()) {
			root.defaultTemplateId = VANILLA_BOX_ID;
		}
		TemplateOperationResult<Void> savedRoot = repository.saveRoot(root);
		if (!savedRoot.success()) {
			return TemplateOperationResult.failure(savedRoot.errorMessage(), savedRoot.error());
		}

		TemplateOperationResult<RootConfig> verifiedRoot = repository.loadRoot();
		if (!verifiedRoot.success() || !isCompleted(verifiedRoot.value())) {
			return TemplateOperationResult.failure("Проверка корневой конфигурации миграции не пройдена",
					verifiedRoot.error());
		}
		return TemplateOperationResult.success(verifiedTemplate.value());
	}

	static ServerTemplate fromLegacy(ResponderConfig legacy) {
		ServerTemplate template = ServerTemplate.empty(VANILLA_BOX_ID, VANILLA_BOX_NAME);
		template.responderEnabled = legacy.enabled;
		template.rules = ServerTemplate.copyRules(legacy.rules);
		template.globalPrefix = legacy.globalPrefix;
		template.clanReplyPrefix = legacy.clanReplyPrefix;
		template.privateReplyCommand = legacy.privateReplyCommand;
		template.globalMarkers = legacy.globalMarkers;
		template.clanMarkers = legacy.clanMarkers;
		template.privateMarkers = legacy.privateMarkers;
		template.mutedWords.addAll(legacy.mutedWords);
		template.discordChatEnabled = Boolean.TRUE.equals(legacy.discordChatEnabled);
		template.discordMutedPlayers.addAll(legacy.discordMutedPlayers);
		template.friends.addAll(legacy.friends);
		template.friendLastSeen.putAll(legacy.friendLastSeen);
		template.friendHudEnabled = Boolean.TRUE.equals(legacy.friendHudEnabled);
		template.friendSoundEnabled = true;
		for (PeriodicMessageConfig entry : legacy.periodicMessages) {
			template.periodicMessages.add(entry.copy());
		}
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		return template;
	}

	private TemplateOperationResult<Void> createAndVerifyBackup() {
		Path backupPath = legacyConfigPath.resolveSibling(BACKUP_FILE_NAME);
		try {
			if (!Files.exists(backupPath)) {
				Files.copy(legacyConfigPath, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
			}
			if (!java.util.Arrays.equals(Files.readAllBytes(legacyConfigPath), Files.readAllBytes(backupPath))) {
				return TemplateOperationResult.failure("Backup старого config не прошёл проверку", null);
			}
			return TemplateOperationResult.success(null);
		} catch (IOException error) {
			return TemplateOperationResult.failure("Не удалось создать backup старого config", error);
		}
	}

	private boolean isCompleted(RootConfig root) {
		boolean registered = root.templates.stream().anyMatch(info -> VANILLA_BOX_ID.equals(info.id));
		return registered && repository.loadTemplate(VANILLA_BOX_ID).success();
	}

	private static boolean sameServerData(ServerTemplate expected, ServerTemplate actual) {
		return GSON.toJson(expected).equals(GSON.toJson(actual));
	}
}
