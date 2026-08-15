package ru.gasada.chatresponder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record PlayerLookupData(String lastSeen, Map<String, String> fields) {
	public PlayerLookupData {
		fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
	}

	public boolean hasData() {
		return lastSeen != null || !fields.isEmpty();
	}
}
