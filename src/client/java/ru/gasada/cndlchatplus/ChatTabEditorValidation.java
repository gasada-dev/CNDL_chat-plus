package ru.gasada.cndlchatplus;

import java.util.Collection;
import java.util.List;

final class ChatTabEditorValidation {
	private ChatTabEditorValidation() {
	}

	static String error(String name, Collection<ChatTabSource> sources, String editedId,
			List<CustomChatTab> existing) {
		String normalizedName = name == null ? "" : name.trim();
		if (normalizedName.isEmpty()) return "Введите название вкладки";
		if (normalizedName.length() > ResponderConfig.MAX_CUSTOM_CHAT_TAB_NAME_LENGTH) {
			return "Название слишком длинное";
		}
		if (sources == null || sources.isEmpty()) return "Выберите хотя бы один канал";
		for (CustomChatTab tab : existing) {
			if ((editedId == null || !editedId.equals(tab.id)) && normalizedName.equalsIgnoreCase(tab.name)) {
				return "Вкладка с таким названием уже существует";
			}
		}
		return null;
	}
}
