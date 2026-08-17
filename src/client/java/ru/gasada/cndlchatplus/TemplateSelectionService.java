package ru.gasada.cndlchatplus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class TemplateSelectionService {
	private final ServerTemplateRepository repository;
	private final ServerTemplateRuntime runtime;
	private final ResponderConfig configView;
	private final ServerTemplateResolver resolver = new ServerTemplateResolver();
	private Object lastConnection;
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
		String id = root.defaultTemplateId;
		return select(id);
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.getConnection() == null) {
			lastConnection = null;
			currentAddress = null;
			return;
		}
		if (lastConnection == minecraft.getConnection()) {
			return;
		}
		lastConnection = minecraft.getConnection();
		ServerData server = minecraft.getCurrentServer();
		currentAddress = server == null ? null : server.ip;
		if (currentAddress == null) {
			return;
		}
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			runtime.clear();
			return;
		}
		ServerTemplateResolver.Resolution resolution = resolver.resolve(loaded.value(), currentAddress);
		if (!resolution.resolved() || !select(resolution.templateId()).success()) {
			runtime.clear();
		}
	}

	public TemplateOperationResult<ServerTemplate> select(String id) {
		TemplateOperationResult<ServerTemplate> loaded = repository.loadTemplate(id);
		if (!loaded.success()) {
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
