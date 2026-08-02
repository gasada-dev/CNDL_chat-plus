package ru.gasada.chatresponder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ServerTemplateRuntime {
	private final TemplateSwitchCoordinator switchCoordinator;
	private final Map<String, String> temporaryOverrides = new LinkedHashMap<>();
	private volatile ActiveTemplateSnapshot activeSnapshot;
	private long generation;

	public ServerTemplateRuntime(TemplateSwitchCoordinator switchCoordinator) {
		this.switchCoordinator = switchCoordinator;
	}

	public synchronized ActiveTemplateSnapshot switchTo(ServerTemplate template) {
		if (template == null) {
			throw new IllegalArgumentException("template must not be null");
		}
		switchCoordinator.resetAll();
		temporaryOverrides.clear();
		activeSnapshot = ActiveTemplateSnapshot.from(template, ++generation);
		return activeSnapshot;
	}

	public synchronized void clear() {
		switchCoordinator.resetAll();
		temporaryOverrides.clear();
		activeSnapshot = null;
		generation++;
	}

	public Optional<ActiveTemplateSnapshot> activeSnapshot() {
		return Optional.ofNullable(activeSnapshot);
	}

	public synchronized void putTemporaryOverride(String key, String value) {
		temporaryOverrides.put(key, value);
	}

	synchronized int temporaryOverrideCount() {
		return temporaryOverrides.size();
	}
}
