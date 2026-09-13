package ru.gasada.cndlchatplus;

import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class ResponderConfigJson {
	private ResponderConfigJson() {
	}

	static ResponderConfig read(Gson gson, String serialized) {
		JsonElement root = JsonParser.parseString(serialized);
		if (!root.isJsonObject()) return gson.fromJson(root, ResponderConfig.class);
		JsonObject compatible = root.getAsJsonObject().deepCopy();
		JsonElement enabled = compatible.remove("chatAlertsEnabled");
		JsonElement rules = compatible.remove("chatAlertRules");
		JsonElement customTabs = compatible.remove("customChatTabs");
		JsonElement hiddenTabs = compatible.remove("hiddenBuiltInTabs");
		ResponderConfig config = gson.fromJson(compatible, ResponderConfig.class);
		if (config == null) return null;
		config.chatAlertsEnabled = enabled != null && enabled.isJsonPrimitive()
				&& enabled.getAsJsonPrimitive().isBoolean() ? enabled.getAsBoolean() : true;
		config.chatAlertRules = new ArrayList<>();
		if (rules != null && rules.isJsonArray()) {
			for (JsonElement entry : rules.getAsJsonArray()) {
				if (!entry.isJsonObject()) continue;
				try {
					ChatAlertRule rule = gson.fromJson(entry, ChatAlertRule.class);
					if (rule != null) config.chatAlertRules.add(rule);
				} catch (RuntimeException error) {
					String id = entry.getAsJsonObject().has("id")
							&& entry.getAsJsonObject().get("id").isJsonPrimitive()
							? entry.getAsJsonObject().get("id").getAsString() : "без ID";
					CndlChatPlusClient.LOGGER.warn("Alert-правило {} пропущено: повреждённые поля", id);
				}
			}
		}
		if (customTabs != null && customTabs.isJsonNull()) {
			config.customChatTabs = null;
		} else if (customTabs != null && customTabs.isJsonArray()) {
			config.customChatTabs = new ArrayList<>();
			for (JsonElement entry : customTabs.getAsJsonArray()) {
				if (!entry.isJsonObject()) continue;
				try {
					CustomChatTab tab = gson.fromJson(entry, CustomChatTab.class);
					JsonObject object = entry.getAsJsonObject();
					if (tab != null && !object.has("sources") && object.has("marker")
							&& object.get("marker").isJsonPrimitive()
							&& object.get("marker").getAsJsonPrimitive().isString()) {
						tab.sources = new ArrayList<>(ChatTabSource.fromLegacyMarker(
								object.get("marker").getAsString()));
					}
					if (tab != null) config.customChatTabs.add(tab);
				} catch (RuntimeException error) {
					String id = entry.getAsJsonObject().has("id")
							&& entry.getAsJsonObject().get("id").isJsonPrimitive()
							? entry.getAsJsonObject().get("id").getAsString() : "без ID";
					CndlChatPlusClient.LOGGER.warn("Пользовательская вкладка {} пропущена: повреждённые поля", id);
				}
			}
		}
		if (hiddenTabs != null && hiddenTabs.isJsonNull()) {
			config.hiddenBuiltInTabs = null;
		} else if (hiddenTabs != null && hiddenTabs.isJsonArray()) {
			config.hiddenBuiltInTabs = new ArrayList<>();
			for (JsonElement entry : hiddenTabs.getAsJsonArray()) {
				if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
					config.hiddenBuiltInTabs.add(entry.getAsString());
				}
			}
		}
		return config;
	}
}
