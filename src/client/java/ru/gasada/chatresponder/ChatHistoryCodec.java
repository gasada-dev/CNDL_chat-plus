package ru.gasada.chatresponder;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

final class ChatHistoryCodec {
	String toJson(Component component) {
		DynamicOps<JsonElement> ops = jsonOps();
		if (ops == null) {
			return null;
		}
		try {
			JsonElement element = ComponentSerialization.CODEC.encodeStart(ops, component)
					.result().orElse(null);
			return element == null ? null : element.toString();
		} catch (Exception exception) {
			GasadaChatResponderClient.LOGGER.warn("Не удалось сериализовать сообщение для истории: {}",
					exception.toString());
			return null;
		}
	}

	Component fromJson(String json) {
		DynamicOps<JsonElement> ops = jsonOps();
		if (ops == null) {
			return null;
		}
		try {
			return ComponentSerialization.CODEC.parse(ops, JsonParser.parseString(json))
					.result().orElse(null);
		} catch (Exception exception) {
			return null;
		}
	}

	private static DynamicOps<JsonElement> jsonOps() {
		Minecraft minecraft = Minecraft.getInstance();
		HolderLookup.Provider registries = minecraft.level != null
				? minecraft.level.registryAccess()
				: minecraft.getConnection() == null ? null : minecraft.getConnection().registryAccess();
		return registries == null ? null : registries.createSerializationContext(JsonOps.INSTANCE);
	}
}
