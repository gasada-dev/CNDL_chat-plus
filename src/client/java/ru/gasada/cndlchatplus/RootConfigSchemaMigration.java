package ru.gasada.cndlchatplus;

import java.util.List;

public final class RootConfigSchemaMigration {
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

		root.schemaVersion = RootConfig.CURRENT_SCHEMA_VERSION;
		TemplateOperationResult<Void> rootSaved = repository.saveRoot(root);
		if (!rootSaved.success()) {
			return TemplateOperationResult.failure(rootSaved.errorMessage(), rootSaved.error());
		}
		TemplateOperationResult<RootConfig> verifiedRoot = repository.loadRoot();
		if (!verifiedRoot.success() || verifiedRoot.value().schemaVersion != RootConfig.CURRENT_SCHEMA_VERSION) {
			return TemplateOperationResult.failure("Не удалось проверить новую схему шаблонов", verifiedRoot.error());
		}
		return TemplateOperationResult.success(new MigrationReport(true, List.of()));
	}

	public record MigrationReport(boolean migrated, List<String> warnings) { }
}
