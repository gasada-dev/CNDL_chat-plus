package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

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
}
