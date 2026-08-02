package ru.gasada.chatresponder;

public final class ServerTemplateManager {
	private final ServerTemplateRepository repository;

	public ServerTemplateManager(ServerTemplateRepository repository) {
		this.repository = repository;
	}

	public TemplateOperationResult<ServerTemplate> createEmpty(String id, String name) {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		RootConfig root = loaded.value();
		if (root.templates.stream().anyMatch(info -> id != null && id.equals(info.id))) {
			return TemplateOperationResult.failure("Шаблон с таким ID уже существует", null);
		}

		ServerTemplate template = ServerTemplate.empty(id, name);
		TemplateOperationResult<Void> savedTemplate = repository.saveTemplate(template);
		if (!savedTemplate.success()) {
			return TemplateOperationResult.failure(savedTemplate.errorMessage(), savedTemplate.error());
		}
		root.templates.add(new ServerTemplateInfo(id, name));
		TemplateOperationResult<Void> savedRoot = repository.saveRoot(root);
		if (!savedRoot.success()) {
			return TemplateOperationResult.failure(savedRoot.errorMessage(), savedRoot.error());
		}
		return TemplateOperationResult.success(template);
	}

	public TemplateOperationResult<ServerTemplate> copy(String sourceId, String newId, String newName) {
		TemplateOperationResult<ServerTemplate> source = repository.loadTemplate(sourceId);
		if (!source.success()) {
			return source;
		}
		TemplateOperationResult<RootConfig> loadedRoot = repository.loadRoot();
		if (!loadedRoot.success()) {
			return TemplateOperationResult.failure(loadedRoot.errorMessage(), loadedRoot.error());
		}
		if (loadedRoot.value().templates.stream().anyMatch(info -> newId != null && newId.equals(info.id))) {
			return TemplateOperationResult.failure("Шаблон с таким ID уже существует", null);
		}

		ServerTemplate copy = source.value().deepCopy(newId, newName);
		TemplateOperationResult<Void> savedTemplate = repository.saveTemplate(copy);
		if (!savedTemplate.success()) {
			return TemplateOperationResult.failure(savedTemplate.errorMessage(), savedTemplate.error());
		}
		loadedRoot.value().templates.add(new ServerTemplateInfo(newId, newName));
		TemplateOperationResult<Void> savedRoot = repository.saveRoot(loadedRoot.value());
		if (!savedRoot.success()) {
			return TemplateOperationResult.failure(savedRoot.errorMessage(), savedRoot.error());
		}
		return TemplateOperationResult.success(copy);
	}
}
