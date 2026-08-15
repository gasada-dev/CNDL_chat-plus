package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class VanillaGameProfileClientTest {
	@Test
	void parsesPublicProfileWithoutConstructionScoreOrHiddenContactPlaceholder() {
		String json = """
				{
				  "username":"Player_1",
				  "reg_date":"2025-01-02",
				  "last_login":"сегодня",
				  "building_score":95,
				  "show_contacts":false,
				  "clan":{"tag":"TAG","name":"Clan","leader":true},
				  "marry":{"partner":"Partner","date":"2026-01-01","surname":"Family"},
				  "buildings":[{"title":"Дом","average_rating":4.8}]
				}
				""";
		var result = VanillaGameProfileClient.parseResponse(200, "application/json; charset=utf-8",
				json.getBytes(StandardCharsets.UTF_8));
		assertTrue(result.success());
		assertEquals("Player_1", result.profile().username());
		assertEquals("TAG", result.profile().clan().tag());
		assertTrue(result.profile().clan().playerIsLeader());
		assertNull(result.profile().clan().leaderName());
		assertEquals("Partner", result.profile().marriage().partner());
		assertEquals("Дом", result.profile().buildings().getFirst().title());
		assertEquals("4.8", result.profile().buildings().getFirst().rating());
		assertNull(result.profile().telegram());
		assertNull(result.profile().vk());
		assertNull(result.profile().website());
	}

	@Test
	void acceptsPartialUnicodeProfileAndRealContacts() {
		String json = """
				{"username":"Player","about":"Привет, мир!","city":"Москва",
				 "telegram":"@player","vk":"https://vk.com/player","website":"https://example.org"}
				""";
		var result = VanillaGameProfileClient.parseResponse(200, "application/json",
				json.getBytes(StandardCharsets.UTF_8));
		assertTrue(result.success());
		assertEquals("Москва", result.profile().city());
		assertEquals("@player", result.profile().telegram());
		assertTrue(result.profile().buildings().isEmpty());
	}

	@Test
	void rejectsStatusContentTypeMalformedUtf8AndOversizedBody() {
		assertFalse(VanillaGameProfileClient.parseResponse(500, "application/json", new byte[]{'x'}).success());
		assertFalse(VanillaGameProfileClient.parseResponse(200, "text/html", new byte[]{'x'}).success());
		assertFalse(VanillaGameProfileClient.parseResponse(200, "application/json", new byte[]{(byte) 0xC3}).success());
		assertFalse(VanillaGameProfileClient.parseResponse(200, "application/json",
				new byte[VanillaGameProfileClient.MAX_BODY_BYTES + 1]).success());
	}
}
