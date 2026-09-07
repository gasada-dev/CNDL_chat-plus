package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

final class VnbxBridgeClientTest {
	@Test
	void storesGenericMessagesByType() {
		VnbxBridgeClient client = new VnbxBridgeClient();
		byte[] payload = """
				{"protocol":1,"type":"clan_update","data":{"tag":"VNBX"}}
				""".strip().getBytes(StandardCharsets.UTF_8);

		assertTrue(client.receive(payload));
		assertEquals("VNBX", client.latest("clan_update").orElseThrow()
				.getAsJsonObject("data").get("tag").getAsString());
	}

	@Test
	void rejectsInvalidAndOversizedMessagesAndResetsSession() {
		VnbxBridgeClient client = new VnbxBridgeClient();
		assertFalse(client.receive("{}".getBytes(StandardCharsets.UTF_8)));
		assertFalse(client.receive("{\"protocol\":\"1\",\"type\":\"server_ack\"}".getBytes(StandardCharsets.UTF_8)));
		assertFalse(client.receive("{\"protocol\":1.5,\"type\":\"server_ack\"}".getBytes(StandardCharsets.UTF_8)));
		assertFalse(client.receive("{\"protocol\":2,\"type\":\"server_ack\"}".getBytes(StandardCharsets.UTF_8)));
		assertFalse(client.receive(new byte[VnbxBridgeClient.MAX_PAYLOAD_BYTES + 1]));
		byte[] malformedUtf8 = "{\"protocol\":1,\"type\":\"server_ack\",\"data\":\"x\"}".getBytes(StandardCharsets.UTF_8);
		malformedUtf8[malformedUtf8.length - 3] = (byte) 0x80;
		assertFalse(client.receive(malformedUtf8));
		assertTrue(client.receive("{\"protocol\":1,\"type\":\"server_ack\"}".getBytes(StandardCharsets.UTF_8)));

		client.reset();
		assertTrue(client.latest("server_ack").isEmpty());
	}

	@Test
	void createsRawUtf8RequestWithoutWriteUtfFraming() {
		byte[] payload = VnbxBridgeClient.request("request_snapshot");
		assertEquals('{', payload[0]);
		assertEquals(1, JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
				.getAsJsonObject().get("protocol").getAsInt());
	}

	@Test
	void buildsEscapedRelationsEventJson() {
		JsonObject event = JsonParser.parseString(VnbxBridgeClient.eventJson("relations_response_accepted",
				"id-1", "Player_\"1", null, null)).getAsJsonObject();

		assertEquals("relations_response_accepted", event.get("event").getAsString());
		assertEquals("Player_\"1", event.get("target").getAsString());
		assertEquals("bridge", event.get("source").getAsString());

		JsonObject failed = JsonParser.parseString(VnbxBridgeClient.eventJson("relations_request_failed",
				"id-1", "Player_1", "timeout", null)).getAsJsonObject();
		JsonObject fallback = JsonParser.parseString(VnbxBridgeClient.eventJson("clan_fallback_decision",
				"id-1", "Player_1", "bridge_unavailable", "sent")).getAsJsonObject();
		JsonObject rejected = JsonParser.parseString(VnbxBridgeClient.eventJson("relations_response_rejected",
				"unknown", "Player_1", "request_id_mismatch", null)).getAsJsonObject();
		assertEquals("timeout", failed.get("reason").getAsString());
		assertEquals("sent", fallback.get("decision").getAsString());
		assertEquals("bridge_unavailable", fallback.get("reason").getAsString());
		assertEquals("request_id_mismatch", rejected.get("reason").getAsString());
	}

	@Test
	void correlatesStrictPlayerRelationsResponse() {
		VnbxBridgeClient client = new VnbxBridgeClient();
		AtomicReference<byte[]> sent = new AtomicReference<>();
		var future = client.requestPlayerRelations("caller-id", "Player_1", sent::set);
		JsonObject request = json(sent.get());
		assertEquals("request_player_relations", request.get("type").getAsString());
		assertEquals("Player_1", request.get("player").getAsString());

		String requestId = request.get("requestId").getAsString();
		assertEquals("caller-id", requestId);
		assertFalse(client.receive(response(requestId, "Other", true, true)));
		assertFalse(future.isDone());
		assertTrue(client.receive(response(requestId, "player_1", true, true)));
		VnbxPlayerRelationsResult result = future.join();
		assertEquals("TAG", result.clan().tag());
		assertEquals("Офицер", result.clan().rank());
		assertEquals("Partner_2", result.marriage().partnerName());
		assertEquals("player_1", client.latest("player_relations").orElseThrow().get("player").getAsString());
		assertFalse(client.receive(response(requestId, "Player_1", true, true)));
	}

	@Test
	void rejectsMalformedRelationsAndCompletesPendingOnResetAndTimeout() throws Exception {
		VnbxBridgeClient client = new VnbxBridgeClient();
		AtomicReference<byte[]> sent = new AtomicReference<>();
		var pending = client.requestPlayerRelations("Player_1", sent::set);
		String requestId = json(sent.get()).get("requestId").getAsString();
		assertFalse(client.receive(("{\"protocol\":1,\"type\":\"player_relations\","
				+ "\"requestId\":\"" + requestId + "\",\"player\":\"Player_1\","
				+ "\"clanAvailable\":true,\"marriageAvailable\":false,"
				+ "\"clan\":{\"inClan\":\"yes\",\"tag\":null,\"name\":null,\"rank\":null},"
				+ "\"marriage\":{\"married\":false,\"partnerUuid\":null,\"partnerName\":null}}")
				.getBytes(StandardCharsets.UTF_8)));
		JsonObject missingNullableField = json(response(requestId, "Player_1", true, true));
		missingNullableField.getAsJsonObject("clan").remove("rank");
		assertFalse(client.receive(missingNullableField.toString().getBytes(StandardCharsets.UTF_8)));
		JsonObject oversizedField = json(response(requestId, "Player_1", true, true));
		oversizedField.getAsJsonObject("clan").addProperty("name", "x".repeat(129));
		assertFalse(client.receive(oversizedField.toString().getBytes(StandardCharsets.UTF_8)));
		assertFalse(pending.isDone());
		client.reset();
		assertFalse(pending.join().available());

		VnbxBridgeClient expiring = new VnbxBridgeClient(1);
		var timedOut = expiring.requestPlayerRelations("Player_1", ignored -> { });
		assertFalse(timedOut.get(1, TimeUnit.SECONDS).available());
		assertNull(expiring.latest("player_relations").orElse(null));
	}

	@Test
	void parsesOptionalExtendedRelationsFields() {
		VnbxBridgeClient client = new VnbxBridgeClient();
		AtomicReference<byte[]> sent = new AtomicReference<>();
		var future = client.requestPlayerRelations("ext-id", "Player_1", sent::set);

		assertTrue(client.receive(("{\"protocol\":1,\"type\":\"player_relations\",\"requestId\":\"ext-id\","
				+ "\"player\":\"Player_1\",\"clanAvailable\":true,\"marriageAvailable\":true,"
				+ "\"clan\":{\"inClan\":true,\"tag\":\"TAG\",\"name\":\"Clan\",\"rank\":\"Офицер\","
				+ "\"leader\":\"Leader_1\"},"
				+ "\"marriage\":{\"married\":true,\"partnerUuid\":\"uuid\",\"partnerName\":\"Partner_2\","
				+ "\"date\":\"01.02.2026\",\"surname\":\"Иванов\"},"
				+ "\"fields\":{\"Статус\":\"Активен\",\"КПД / KDR\":\"1.5\",\"bad\":123,"
				+ "\"\":\"skip\",\"\":null}}").getBytes(StandardCharsets.UTF_8)));
		VnbxPlayerRelationsResult result = future.join();
		assertEquals("Leader_1", result.clan().leader());
		assertEquals("01.02.2026", result.marriage().date());
		assertEquals("Иванов", result.marriage().surname());
		assertEquals(java.util.List.of("Статус", "КПД / KDR"), java.util.List.copyOf(result.fields().keySet()));
		assertEquals("1.5", result.fields().get("КПД / KDR"));
	}

	@Test
	void rejectsInvalidOptionalLeaderButAcceptsAbsent() {
		VnbxBridgeClient client = new VnbxBridgeClient();
		AtomicReference<byte[]> sent = new AtomicReference<>();
		var future = client.requestPlayerRelations("ext-id", "Player_1", sent::set);
		JsonObject invalid = json(response("ext-id", "Player_1", true, true));
		invalid.getAsJsonObject("clan").addProperty("leader", 42);
		assertFalse(client.receive(invalid.toString().getBytes(StandardCharsets.UTF_8)));
		assertFalse(future.isDone());

		assertTrue(client.receive(response("ext-id", "Player_1", true, true)));
		VnbxPlayerRelationsResult result = future.join();
		assertNull(result.clan().leader());
		assertNull(result.marriage().date());
		assertNull(result.marriage().surname());
		assertTrue(result.fields().isEmpty());
	}

	private static JsonObject json(byte[] payload) {
		return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static byte[] response(String requestId, String player, boolean clanAvailable,
			boolean marriageAvailable) {
		return ("{\"protocol\":1,\"type\":\"player_relations\",\"requestId\":\"" + requestId
				+ "\",\"player\":\"" + player + "\",\"clanAvailable\":" + clanAvailable
				+ ",\"marriageAvailable\":" + marriageAvailable
				+ ",\"clan\":{\"inClan\":true,\"tag\":\"TAG\",\"name\":\"Clan\",\"rank\":\"Офицер\"}"
				+ ",\"marriage\":{\"married\":true,\"partnerUuid\":\"uuid\","
				+ "\"partnerName\":\"Partner_2\"}}").getBytes(StandardCharsets.UTF_8);
	}
}
