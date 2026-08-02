package ru.gasada.chatresponder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class ServerTemplateRuntime {
	private final TemplateSwitchCoordinator switchCoordinator;
	private final Map<String, String> temporaryOverrides = new LinkedHashMap<>();
	private volatile ActiveTemplateSnapshot activeSnapshot;
	private volatile CompiledParserSettings compiledParsers;
	private volatile CompiledFilterSet compiledFilters;
	private volatile ReplyRuleMatcher compiledReplyRules;
	private ServerTemplate activeTemplate;
	private long generation;

	public ServerTemplateRuntime(TemplateSwitchCoordinator switchCoordinator) {
		this.switchCoordinator = switchCoordinator;
	}

	public static ServerTemplateRuntime fromLegacyConfig(ResponderConfig config) {
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(LegacyConfigToVanillaBoxMigration.fromLegacy(config));
		return runtime;
	}

	public synchronized ActiveTemplateSnapshot switchTo(ServerTemplate template) {
		if (template == null) {
			throw new IllegalArgumentException("template must not be null");
		}
		switchCoordinator.resetAll();
		temporaryOverrides.clear();
		activeTemplate = template.deepCopy(template.id, template.name);
		publishActiveTemplate();
		return activeSnapshot;
	}

	public synchronized Optional<ActiveTemplateSnapshot> updateActiveTemplate(Consumer<ServerTemplate> update) {
		if (activeTemplate == null) {
			return Optional.empty();
		}
		update.accept(activeTemplate);
		publishActiveTemplate();
		return Optional.of(activeSnapshot);
	}

	private void publishActiveTemplate() {
		ActiveTemplateSnapshot nextSnapshot = ActiveTemplateSnapshot.from(activeTemplate, ++generation);
		CompiledParserSettings nextParsers = CompiledParserSettings.compile(nextSnapshot.parsers());
		CompiledFilterSet nextFilters = CompiledFilterSet.compile(nextSnapshot);
		ReplyRuleMatcher nextRules = ReplyRuleMatcher.compile(nextSnapshot.rules());
		compiledParsers = nextParsers;
		compiledFilters = nextFilters;
		compiledReplyRules = nextRules;
		activeSnapshot = nextSnapshot;
	}

	public synchronized void clear() {
		switchCoordinator.resetAll();
		temporaryOverrides.clear();
		activeSnapshot = null;
		activeTemplate = null;
		compiledParsers = null;
		compiledFilters = null;
		compiledReplyRules = null;
		generation++;
	}

	public Optional<ActiveTemplateSnapshot> activeSnapshot() {
		return Optional.ofNullable(activeSnapshot);
	}

	public Optional<CompiledParserSettings> compiledParsers() {
		return Optional.ofNullable(compiledParsers);
	}

	public Optional<CompiledFilterSet> compiledFilters() {
		return Optional.ofNullable(compiledFilters);
	}

	public Optional<ReplyRuleMatcher> compiledReplyRules() {
		return Optional.ofNullable(compiledReplyRules);
	}

	public synchronized void putTemporaryOverride(String key, String value) {
		temporaryOverrides.put(key, value);
	}

	synchronized int temporaryOverrideCount() {
		return temporaryOverrides.size();
	}
}
