package ru.gasada.chatresponder;

public final class ServerTemplateManager {
	private final ServerTemplateRepository repository;

	public ServerTemplateManager(ServerTemplateRepository repository) {
		this.repository = repository;
	}

	public TemplateOperationResult<ServerTemplate> createEmpty(String id, String name) {
		TemplateNameValidator.ValidationResult nameResult = TemplateNameValidator.validate(name);
		if (!nameResult.valid()) {
			return TemplateOperationResult.failure(nameResult.errorMessage(), null);
		}
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		RootConfig root = loaded.value();
		if (root.templates.stream().anyMatch(info -> id != null && id.equals(info.id))) {
			return TemplateOperationResult.failure("Шаблон с таким ID уже существует", null);
		}

		ServerTemplate template = ServerTemplate.empty(id, nameResult.normalizedName());
		TemplateOperationResult<Void> savedTemplate = repository.saveTemplate(template);
		if (!savedTemplate.success()) {
			return TemplateOperationResult.failure(savedTemplate.errorMessage(), savedTemplate.error());
		}
		root.templates.add(new ServerTemplateInfo(id, nameResult.normalizedName()));
		TemplateOperationResult<Void> savedRoot = repository.saveRoot(root);
		if (!savedRoot.success()) {
			return TemplateOperationResult.failure(savedRoot.errorMessage(), savedRoot.error());
		}
		return TemplateOperationResult.success(template);
	}

	public TemplateOperationResult<ServerTemplate> copy(String sourceId, String newId, String newName) {
		TemplateNameValidator.ValidationResult nameResult = TemplateNameValidator.validate(newName);
		if (!nameResult.valid()) {
			return TemplateOperationResult.failure(nameResult.errorMessage(), null);
		}
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

		ServerTemplate copy = source.value().deepCopy(newId, nameResult.normalizedName());
		TemplateOperationResult<Void> savedTemplate = repository.saveTemplate(copy);
		if (!savedTemplate.success()) {
			return TemplateOperationResult.failure(savedTemplate.errorMessage(), savedTemplate.error());
		}
		loadedRoot.value().templates.add(new ServerTemplateInfo(newId, nameResult.normalizedName()));
		TemplateOperationResult<Void> savedRoot = repository.saveRoot(loadedRoot.value());
		if (!savedRoot.success()) {
			return TemplateOperationResult.failure(savedRoot.errorMessage(), savedRoot.error());
		}
		return TemplateOperationResult.success(copy);
	}

	public TemplateOperationResult<ServerTemplate> importNew(ServerTemplate source) {
		if (source == null || !ServerTemplateRepository.isSafeId(source.id)) {
			return TemplateOperationResult.failure("Некорректный ID импортируемого шаблона", null);
		}
		TemplateNameValidator.ValidationResult nameResult = TemplateNameValidator.validate(source.name);
		if (!nameResult.valid()) {
			return TemplateOperationResult.failure(nameResult.errorMessage(), null);
		}
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		if (loaded.value().templates.stream().anyMatch(info -> source.id.equals(info.id))
				|| repository.loadTemplate(source.id).success()) {
			return TemplateOperationResult.failure("Шаблон с таким ID уже существует", null);
		}

		ServerTemplate imported = source.deepCopy(source.id, nameResult.normalizedName());
		TemplateOperationResult<Void> savedTemplate = repository.saveTemplate(imported);
		if (!savedTemplate.success()) {
			return TemplateOperationResult.failure(savedTemplate.errorMessage(), savedTemplate.error());
		}
		loaded.value().templates.add(new ServerTemplateInfo(imported.id, imported.name));
		TemplateOperationResult<Void> savedRoot = repository.saveRoot(loaded.value());
		if (!savedRoot.success()) {
			TemplateOperationResult<Void> cleanup = repository.deleteTemplate(imported.id);
			if (!cleanup.success()) {
				return TemplateOperationResult.failure(savedRoot.errorMessage()
						+ "; не удалось удалить незарегистрированный файл: " + cleanup.errorMessage(),
						savedRoot.error());
			}
			return TemplateOperationResult.failure(savedRoot.errorMessage(), savedRoot.error());
		}
		return TemplateOperationResult.success(imported);
	}

	public TemplateOperationResult<ServerTemplate> saveDraft(ServerTemplate draft, String name,
			java.util.List<String> addressPatterns) {
		TemplateNameValidator.ValidationResult nameResult = TemplateNameValidator.validate(name);
		if (!nameResult.valid()) {
			return TemplateOperationResult.failure(nameResult.errorMessage(), null);
		}
		java.util.List<String> normalizedPatterns = new java.util.ArrayList<>();
		for (String source : addressPatterns) {
			AddressPatternValidator.ValidationResult result = AddressPatternValidator.validate(source);
			if (!result.valid()) {
				return TemplateOperationResult.failure(result.errorMessage(), null);
			}
			if (normalizedPatterns.stream().noneMatch(value -> value.equalsIgnoreCase(result.normalizedPattern()))) {
				normalizedPatterns.add(result.normalizedPattern());
			}
		}
		draft.name = nameResult.normalizedName();
		TemplateOperationResult<Void> saved = repository.saveTemplate(draft);
		if (!saved.success()) {
			return TemplateOperationResult.failure(saved.errorMessage(), saved.error());
		}
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		ServerTemplateInfo info = loaded.value().templates.stream()
				.filter(value -> draft.id.equals(value.id)).findFirst().orElse(null);
		if (info == null) {
			return TemplateOperationResult.failure("Шаблон не зарегистрирован", null);
		}
		info.name = draft.name;
		info.addressPatterns = normalizedPatterns;
		TemplateOperationResult<Void> rootSaved = repository.saveRoot(loaded.value());
		return rootSaved.success() ? TemplateOperationResult.success(draft)
				: TemplateOperationResult.failure(rootSaved.errorMessage(), rootSaved.error());
	}

	public TemplateOperationResult<Void> setDefault(String id) {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		if (loaded.value().templates.stream().noneMatch(info -> id.equals(info.id))) {
			return TemplateOperationResult.failure("Неизвестный шаблон", null);
		}
		loaded.value().defaultTemplateId = id;
		return repository.saveRoot(loaded.value());
	}

	public TemplateOperationResult<Void> bindAddress(String address, String id) {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		TemplateOperationResult<Void> bound = new ServerTemplateResolver().bindExact(loaded.value(), address, id);
		return bound.success() ? repository.saveRoot(loaded.value()) : bound;
	}

	public TemplateOperationResult<Void> delete(String id, String activeId) {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			return TemplateOperationResult.failure(loaded.errorMessage(), loaded.error());
		}
		RootConfig root = loaded.value();
		if (id.equals(activeId)) {
			return TemplateOperationResult.failure("Нельзя удалить активный шаблон", null);
		}
		if (root.templates.size() <= 1) {
			return TemplateOperationResult.failure("Нельзя удалить единственный шаблон", null);
		}
		if (id.equals(root.defaultTemplateId)) {
			return TemplateOperationResult.failure("Сначала выберите другой шаблон по умолчанию", null);
		}
		root.templates.removeIf(info -> id.equals(info.id));
		root.serverBindings.entrySet().removeIf(entry -> id.equals(entry.getValue()));
		TemplateOperationResult<Void> savedRoot = repository.saveRoot(root);
		if (!savedRoot.success()) {
			return savedRoot;
		}
		return repository.deleteTemplate(id);
	}
}
