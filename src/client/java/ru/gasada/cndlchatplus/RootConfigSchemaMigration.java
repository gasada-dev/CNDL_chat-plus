package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RootConfigSchemaMigration {
	static final String OLD_GAME_ID = "game";
	static final String VANILLA_GAME_ID = "vanilla-game";

	private final ServerTemplateRepository repository;

	public RootConfigSchemaMigration(ServerTemplateRepository repository) {
		this.repository = repository;
	}

	public TemplateOperationResult<MigrationReport> migrate() {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		RootConfig root = loaded.value();
		if (root.schemaVersion >= RootConfig.CURRENT_SCHEMA_VERSION) {
			return TemplateOperationResult.success(new MigrationReport(false, List.of()));
		}

		List<String> warnings = new ArrayList<>();
		ServerTemplateInfo oldInfo = find(root, OLD_GAME_ID);
		ServerTemplateInfo newInfo = find(root, VANILLA_GAME_ID);
		TemplateOperationResult<ServerTemplate> existingNewFile = repository.loadTemplate(VANILLA_GAME_ID);
		boolean didRename = false;
		if (oldInfo != null && newInfo == null && !existingNewFile.success()) {
			TemplateOperationResult<ServerTemplate> oldTemplate = repository.loadTemplate(OLD_GAME_ID);
			if (!oldTemplate.success()) {
				return TemplateOperationResult.failure(oldTemplate.errorMessage(), oldTemplate.error());
			}
			ServerTemplate renamed = oldTemplate.value().deepCopy(VANILLA_GAME_ID, oldTemplate.value().name);
			renamed.playerInfo.provider = PlayerInfoProvider.VANILLA_GAME_PUBLIC_API;
			renamed.playerInfo.providerConfigured = true;
			TemplateOperationResult<Void> saved = repository.saveTemplate(renamed);
			if (!saved.success()) {
				return TemplateOperationResult.failure(saved.errorMessage(), saved.error());
			}
			TemplateOperationResult<ServerTemplate> verified = repository.loadTemplate(VANILLA_GAME_ID);
			if (!verified.success()) {
				return TemplateOperationResult.failure("Не удалось проверить миграцию шаблона game", verified.error());
			}
			oldInfo.id = VANILLA_GAME_ID;
			oldInfo.name = renamed.name;
			replaceReferences(root, OLD_GAME_ID, VANILLA_GAME_ID);
			didRename = true;
		} else if (oldInfo != null) {
			if (newInfo == null && existingNewFile.success()) {
				root.templates.add(new ServerTemplateInfo(VANILLA_GAME_ID, existingNewFile.value().name));
			}
			warnings.add("Найдены оба шаблона game и vanilla-game; пользовательские данные не объединены");
		}

		root.schemaVersion = RootConfig.CURRENT_SCHEMA_VERSION;
		TemplateOperationResult<Void> rootSaved = repository.saveRoot(root);
		if (!rootSaved.success()) {
			return TemplateOperationResult.failure(rootSaved.errorMessage(), rootSaved.error());
		}
		TemplateOperationResult<RootConfig> verifiedRoot = repository.loadRoot();
		if (!verifiedRoot.success() || verifiedRoot.value().schemaVersion != RootConfig.CURRENT_SCHEMA_VERSION) {
			return TemplateOperationResult.failure("Не удалось проверить новую схему шаблонов", verifiedRoot.error());
		}
		if (didRename) {
			TemplateOperationResult<Void> deleted = repository.deleteTemplate(OLD_GAME_ID);
			if (!deleted.success()) warnings.add(deleted.errorMessage());
		}
		return TemplateOperationResult.success(new MigrationReport(true, List.copyOf(warnings)));
	}

	private static ServerTemplateInfo find(RootConfig root, String id) {
		return root.templates.stream().filter(info -> id.equals(info.id)).findFirst().orElse(null);
	}

	private static void replaceReferences(RootConfig root, String oldId, String newId) {
		if (oldId.equals(root.defaultTemplateId)) root.defaultTemplateId = newId;
		for (Map.Entry<String, String> entry : root.serverBindings.entrySet()) {
			if (oldId.equals(entry.getValue())) entry.setValue(newId);
		}
	}

	public record MigrationReport(boolean migrated, List<String> warnings) { }
}
