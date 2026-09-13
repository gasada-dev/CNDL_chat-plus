package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class ChatTabConfigSanitizer {
	private ChatTabConfigSanitizer() {
	}

	static List<CustomChatTab> customTabs(List<CustomChatTab> source) {
		if (source == null) return new ArrayList<>(List.of(CustomChatTab.defaultVoice()));
		List<CustomChatTab> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (CustomChatTab tab : source) {
			if (tab == null || result.size() >= ResponderConfig.MAX_CUSTOM_CHAT_TABS) continue;
			tab.name = truncate(trim(tab.name), ResponderConfig.MAX_CUSTOM_CHAT_TAB_NAME_LENGTH);
			tab.sources = sources(tab.sources);
			tab.outgoingPrefix = truncate(trim(tab.outgoingPrefix),
					ResponderConfig.MAX_CUSTOM_CHAT_TAB_PREFIX_LENGTH);
			if (tab.sources.isEmpty()) continue;
			if (tab.name.isEmpty()) tab.name = "Вкладка";
			tab.id = truncate(trim(tab.id), ResponderConfig.MAX_CUSTOM_CHAT_TAB_ID_LENGTH);
			if (tab.id.isEmpty() || !ids.add(tab.id)) {
				do tab.id = UUID.randomUUID().toString(); while (!ids.add(tab.id));
			}
			result.add(tab);
		}
		return result;
	}

	private static List<ChatTabSource> sources(List<ChatTabSource> source) {
		if (source == null) return new ArrayList<>();
		List<ChatTabSource> result = new ArrayList<>();
		for (ChatTabSource candidate : ChatTabSource.values()) {
			if (source.contains(candidate)) result.add(candidate);
		}
		return result;
	}

	static List<String> hiddenBuiltIns(List<String> source) {
		if (source == null) return new ArrayList<>();
		List<String> result = new ArrayList<>();
		for (String value : source) {
			if (value == null || value.isBlank()) continue;
			String name = value.trim().toUpperCase(Locale.ROOT);
			for (ChatTab tab : ChatTab.values()) {
				if (tab != ChatTab.ALL && tab.name().equals(name) && !result.contains(name)) result.add(name);
			}
		}
		return result;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
