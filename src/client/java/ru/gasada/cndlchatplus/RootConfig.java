package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RootConfig {
	public static final int CURRENT_SCHEMA_VERSION = 3;

	public int schemaVersion = CURRENT_SCHEMA_VERSION;
	public String defaultTemplateId;
	public List<ServerTemplateInfo> templates = new ArrayList<>();
	public Map<String, String> serverBindings = new LinkedHashMap<>();

	public void sanitize() {
		if (schemaVersion < 1) {
			schemaVersion = CURRENT_SCHEMA_VERSION;
		}
		if (templates == null) {
			templates = new ArrayList<>();
		}
		if (serverBindings == null) {
			serverBindings = new LinkedHashMap<>();
		}
		templates.removeIf(info -> info == null || info.id == null || info.id.isBlank());
		serverBindings.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
				|| entry.getValue() == null || entry.getValue().isBlank());
	}
}
