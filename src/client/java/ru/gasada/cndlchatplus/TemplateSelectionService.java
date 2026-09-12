package ru.gasada.cndlchatplus;

public final class TemplateSelectionService {
	private final ServerTemplateRepository repository;
	private final ServerTemplateRuntime runtime;
	private final ResponderConfig configView;
	private String currentAddress;

	public TemplateSelectionService(ServerTemplateRepository repository, ServerTemplateRuntime runtime,
			ResponderConfig configView) {
		this.repository = repository;
		this.runtime = runtime;
		this.configView = configView;
	}

	public TemplateOperationResult<ServerTemplate> initializeDefault() {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		RootConfig root = loaded.value();
		if (root.templates.isEmpty()) {
			ServerTemplate vanilla = LegacyConfigToVanillaBoxMigration.fromLegacy(configView);
			TemplateOperationResult<Void> templateSaved = repository.saveTemplate(vanilla);
			if (!templateSaved.success()) {
				return TemplateOperationResult.failure(templateSaved.errorMessage(), templateSaved.error());
			}
			root.templates.add(new ServerTemplateInfo(vanilla.id, vanilla.name));
			root.defaultTemplateId = vanilla.id;
			TemplateOperationResult<Void> rootSaved = repository.saveRoot(root);
			if (!rootSaved.success()) {
				return TemplateOperationResult.failure(rootSaved.errorMessage(), rootSaved.error());
			}
		}
		if (root.defaultTemplateId == null || root.defaultTemplateId.isBlank()) {
			root.defaultTemplateId = root.templates.getFirst().id;
			TemplateOperationResult<Void> rootSaved = repository.saveRoot(root);
			if (!rootSaved.success()) {
				return TemplateOperationResult.failure(rootSaved.errorMessage(), rootSaved.error());
			}
		}
		return select(LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID);
	}

	public TemplateOperationResult<ServerTemplate> connect(String normalizedAddress) {
		currentAddress = normalizedAddress;
		return select(LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID);
	}

	public void disconnect() {
		currentAddress = null;
		runtime.clear();
	}

	public TemplateOperationResult<ServerTemplate> select(String ignoredId) {
		TemplateOperationResult<ServerTemplate> loaded = repository.loadTemplate(
				LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID);
		if (!loaded.success()) {
			runtime.clear();
			return loaded;
		}
		LegacyConfigToVanillaBoxMigration.populateLegacyView(configView, loaded.value());
		configView.sanitize();
		runtime.switchTo(loaded.value());
		return loaded;
	}

	public String currentAddress() {
		return currentAddress;
	}
}
