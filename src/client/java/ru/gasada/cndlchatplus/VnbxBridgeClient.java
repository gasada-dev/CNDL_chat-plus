package ru.gasada.cndlchatplus;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class VnbxBridgeClient {
	static final int PROTOCOL_VERSION = 1;
	static final int MAX_PAYLOAD_BYTES = 16 * 1024;
	private static final int MAX_MESSAGE_TYPES = 32;
	private static final Pattern MESSAGE_TYPE = Pattern.compile("[a-z0-9_]{1,64}");
	private final Map<String, JsonObject> latestByType = new HashMap<>();

	boolean receive(byte[] payload) {
		if (payload == null || payload.length > MAX_PAYLOAD_BYTES) return false;
		try {
			String json = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(payload)).toString();
			JsonObject message = JsonParser.parseString(json).getAsJsonObject();
			JsonElement protocol = message.get("protocol");
			JsonElement typeElement = message.get("type");
			if (protocol == null || !protocol.isJsonPrimitive()
					|| !protocol.getAsJsonPrimitive().isNumber()
					|| protocol.getAsBigDecimal().compareTo(BigDecimal.valueOf(PROTOCOL_VERSION)) != 0
					|| typeElement == null || !typeElement.isJsonPrimitive()
					|| !typeElement.getAsJsonPrimitive().isString()) return false;

			String type = typeElement.getAsString();
			if (!MESSAGE_TYPE.matcher(type).matches()
					|| !latestByType.containsKey(type) && latestByType.size() >= MAX_MESSAGE_TYPES) return false;
			latestByType.put(type, message);
			CndlChatPlusClient.LOGGER.debug("VnbxBridge received type={}", type);
			return true;
		} catch (CharacterCodingException | RuntimeException ignored) {
			return false;
		}
	}

	Optional<JsonObject> latest(String type) {
		JsonObject message = latestByType.get(type);
		return Optional.ofNullable(message == null ? null : message.deepCopy());
	}

	void reset() {
		latestByType.clear();
	}

	static byte[] request(String type) {
		JsonObject message = new JsonObject();
		message.addProperty("protocol", PROTOCOL_VERSION);
		message.addProperty("type", type);
		return message.toString().getBytes(StandardCharsets.UTF_8);
	}
}
