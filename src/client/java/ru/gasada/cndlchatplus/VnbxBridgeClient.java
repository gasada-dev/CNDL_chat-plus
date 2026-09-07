package ru.gasada.cndlchatplus;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class VnbxBridgeClient {
	static final int PROTOCOL_VERSION = 1;
	static final int MAX_PAYLOAD_BYTES = 16 * 1024;
	private static final int MAX_MESSAGE_TYPES = 32;
	private static final long REQUEST_TIMEOUT_MS = 5_000;
	private static final Pattern MESSAGE_TYPE = Pattern.compile("[a-z0-9_]{1,64}");
	private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
	private final Map<String, JsonObject> latestByType = new HashMap<>();
	private final Map<String, PendingRequest> pendingRequests = new HashMap<>();
	private final long requestTimeoutMillis;

	VnbxBridgeClient() {
		this(REQUEST_TIMEOUT_MS);
	}

	VnbxBridgeClient(long requestTimeoutMillis) {
		this.requestTimeoutMillis = requestTimeoutMillis;
	}

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
			if (!MESSAGE_TYPE.matcher(type).matches()) return false;
			PendingRequest matched = null;
			if ("player_relations".equals(type)) {
				VnbxPlayerRelationsResult result = parsePlayerRelations(message);
				if (result == null) {
					rejected(message, "malformed_response");
					return false;
				}
				String requestId = result.requestId();
				synchronized (this) {
					PendingRequest pending = pendingRequests.get(requestId);
					if (pending == null) {
						rejected(message, "request_id_mismatch");
						return false;
					}
					if (!pending.player().equalsIgnoreCase(result.player())) {
						rejected(message, "target_mismatch");
						return false;
					}
					if (!latestByType.containsKey(type) && latestByType.size() >= MAX_MESSAGE_TYPES) {
						rejected(message, "message_limit");
						return false;
					}
					pendingRequests.remove(requestId);
					latestByType.put(type, message);
					matched = pending;
				}
				matched.future().complete(result);
				log("relations_response_accepted", result.requestId(), result.player(), null, null);
			} else {
				synchronized (this) {
					if (!latestByType.containsKey(type) && latestByType.size() >= MAX_MESSAGE_TYPES) return false;
					latestByType.put(type, message);
				}
			}
			CndlChatPlusClient.LOGGER.debug("VnbxBridge received type={}", type);
			return true;
		} catch (CharacterCodingException | RuntimeException ignored) {
			return false;
		}
	}

	synchronized Optional<JsonObject> latest(String type) {
		JsonObject message = latestByType.get(type);
		return Optional.ofNullable(message == null ? null : message.deepCopy());
	}

	void reset() {
		reset("reset");
	}

	void reset(String reason) {
		List<CompletableFuture<VnbxPlayerRelationsResult>> futures;
		List<PendingRequest> pending;
		synchronized (this) {
			latestByType.clear();
			pending = new ArrayList<>(pendingRequests.values());
			futures = new ArrayList<>(pending.size());
			pending.forEach(value -> futures.add(value.future()));
			pendingRequests.clear();
		}
		for (int index = 0; index < futures.size(); index++) {
			PendingRequest value = pending.get(index);
			log("relations_request_failed", value.requestId(), value.player(), reason, null);
			futures.get(index).complete(VnbxPlayerRelationsResult.unavailable(value.requestId(), value.player()));
		}
	}

	static String newRequestId() {
		return UUID.randomUUID().toString();
	}

	CompletableFuture<VnbxPlayerRelationsResult> requestPlayerRelations(String player, Consumer<byte[]> sender) {
		return requestPlayerRelations(newRequestId(), player, sender);
	}

	CompletableFuture<VnbxPlayerRelationsResult> requestPlayerRelations(String requestId, String player,
			Consumer<byte[]> sender) {
		if (!PlayerNameValidator.validate(player).valid() || sender == null) {
			log("relations_request_failed", requestId, player, "send_failure", null);
			return CompletableFuture.completedFuture(VnbxPlayerRelationsResult.unavailable(requestId, player));
		}
		CompletableFuture<VnbxPlayerRelationsResult> future = new CompletableFuture<>();
		PendingRequest pending = new PendingRequest(requestId, player, future);
		synchronized (this) {
			pendingRequests.put(requestId, pending);
		}
		try {
			sender.accept(playerRelationsRequest(requestId, player));
		} catch (RuntimeException error) {
			completeUnavailable(requestId, pending, "send_failure");
			return future;
		}
		CompletableFuture.delayedExecutor(requestTimeoutMillis, TimeUnit.MILLISECONDS)
				.execute(() -> completeUnavailable(requestId, pending, "timeout"));
		return future;
	}

	private void completeUnavailable(String requestId, PendingRequest pending, String reason) {
		synchronized (this) {
			if (!pendingRequests.remove(requestId, pending)) return;
		}
		log("relations_request_failed", requestId, pending.player(), reason, null);
		pending.future().complete(VnbxPlayerRelationsResult.unavailable(requestId, pending.player()));
	}

	private static VnbxPlayerRelationsResult parsePlayerRelations(JsonObject message) {
		String requestId = requiredString(message, "requestId", 64);
		String player = requiredString(message, "player", PlayerNameValidator.MAX_LENGTH);
		Boolean clanAvailable = requiredBoolean(message, "clanAvailable");
		Boolean marriageAvailable = requiredBoolean(message, "marriageAvailable");
		JsonObject clan = requiredObject(message, "clan");
		JsonObject marriage = requiredObject(message, "marriage");
		if (requestId == null || !REQUEST_ID.matcher(requestId).matches()
				|| player == null || !PlayerNameValidator.validate(player).valid()
				|| clanAvailable == null || marriageAvailable == null || clan == null || marriage == null) return null;
		Boolean inClan = requiredBoolean(clan, "inClan");
		Boolean married = requiredBoolean(marriage, "married");
		String tag = nullableString(clan, "tag", 64);
		String name = nullableString(clan, "name", 128);
		String rank = nullableString(clan, "rank", 128);
		String leader = optionalString(clan, "leader", PlayerNameValidator.MAX_LENGTH);
		String partnerUuid = nullableString(marriage, "partnerUuid", 64);
		String partnerName = nullableString(marriage, "partnerName", PlayerNameValidator.MAX_LENGTH);
		String marriageDate = optionalString(marriage, "date", 64);
		String marriageSurname = optionalString(marriage, "surname", 128);
		if (inClan == null || married == null || tag == INVALID_STRING || name == INVALID_STRING
				|| rank == INVALID_STRING || leader == INVALID_STRING || partnerUuid == INVALID_STRING
				|| partnerName == INVALID_STRING || marriageDate == INVALID_STRING || marriageSurname == INVALID_STRING
				|| partnerName != null && !PlayerNameValidator.validate(partnerName).valid()
				|| leader != null && !PlayerNameValidator.validate(leader).valid()) return null;
		return new VnbxPlayerRelationsResult(true, requestId, player, clanAvailable,
				new VnbxPlayerRelationsResult.Clan(inClan, tag, name, rank, leader), marriageAvailable,
				new VnbxPlayerRelationsResult.Marriage(married, partnerUuid, partnerName, marriageDate, marriageSurname),
				parseFields(message.get("fields")));
	}

	private static final int MAX_EXTRA_FIELDS = 32;
	private static final int MAX_FIELD_KEY_LENGTH = 64;
	private static final int MAX_FIELD_VALUE_LENGTH = 256;

	private static Map<String, String> parseFields(JsonElement element) {
		if (element == null || !element.isJsonObject()) return Map.of();
		Map<String, String> fields = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
			if (fields.size() >= MAX_EXTRA_FIELDS) break;
			JsonElement value = entry.getValue();
			if (entry.getKey() == null || entry.getKey().isBlank()
					|| entry.getKey().length() > MAX_FIELD_KEY_LENGTH
					|| !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
			String text = value.getAsString();
			if (text.isBlank() || text.length() > MAX_FIELD_VALUE_LENGTH) continue;
			fields.put(entry.getKey(), text);
		}
		return fields;
	}

	private static final String INVALID_STRING = new String();

	private static String requiredString(JsonObject object, String name, int maxLength) {
		String value = nullableString(object, name, maxLength);
		return value == INVALID_STRING || value == null ? null : value;
	}

	private static String optionalString(JsonObject object, String name, int maxLength) {
		if (!object.has(name)) return null;
		return nullableString(object, name, maxLength);
	}

	private static String nullableString(JsonObject object, String name, int maxLength) {
		if (!object.has(name)) return INVALID_STRING;
		JsonElement element = object.get(name);
		if (element.isJsonNull()) return null;
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return INVALID_STRING;
		String value = element.getAsString();
		return value.length() <= maxLength ? value : INVALID_STRING;
	}

	private static Boolean requiredBoolean(JsonObject object, String name) {
		JsonElement element = object.get(name);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
				? element.getAsBoolean() : null;
	}

	private static JsonObject requiredObject(JsonObject object, String name) {
		JsonElement element = object.get(name);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	static void logFallback(String requestId, String target, String decision, String reason) {
		log("clan_fallback_decision", requestId, target, reason, decision);
	}

	static void logRequestFailed(String requestId, String target, String reason) {
		log("relations_request_failed", requestId, target, reason, null);
	}

	private static void log(String event, String requestId, String target, String reason, String decision) {
		CndlChatPlusClient.LOGGER.info(eventJson(event, requestId, target, reason, decision));
	}

	static String eventJson(String event, String requestId, String target, String reason, String decision) {
		JsonObject message = new JsonObject();
		message.addProperty("event", event);
		if (requestId != null) message.addProperty("requestId", requestId);
		if (target != null) message.addProperty("target", target);
		if ("relations_response_accepted".equals(event)) message.addProperty("source", "bridge");
		if (reason != null) message.addProperty("reason", reason);
		if (decision != null) message.addProperty("decision", decision);
		return message.toString();
	}

	private static void rejected(JsonObject message, String reason) {
		log("relations_response_rejected", safeString(message, "requestId", 64),
				safeString(message, "player", PlayerNameValidator.MAX_LENGTH), reason, null);
	}

	private static String safeString(JsonObject message, String name, int maxLength) {
		JsonElement value = message.get(name);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				&& value.getAsString().length() <= maxLength ? value.getAsString() : null;
	}

	static byte[] request(String type) {
		JsonObject message = new JsonObject();
		message.addProperty("protocol", PROTOCOL_VERSION);
		message.addProperty("type", type);
		return message.toString().getBytes(StandardCharsets.UTF_8);
	}

	static byte[] playerRelationsRequest(String requestId, String player) {
		JsonObject message = new JsonObject();
		message.addProperty("protocol", PROTOCOL_VERSION);
		message.addProperty("type", "request_player_relations");
		message.addProperty("requestId", requestId);
		message.addProperty("player", player);
		return message.toString().getBytes(StandardCharsets.UTF_8);
	}

	private record PendingRequest(String requestId, String player, CompletableFuture<VnbxPlayerRelationsResult> future) { }
}
