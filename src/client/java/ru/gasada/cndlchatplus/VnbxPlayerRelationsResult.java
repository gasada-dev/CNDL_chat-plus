package ru.gasada.cndlchatplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record VnbxPlayerRelationsResult(
		boolean available,
		String requestId,
		String player,
		boolean clanAvailable,
		Clan clan,
		boolean marriageAvailable,
		Marriage marriage,
		Map<String, String> fields) {

	VnbxPlayerRelationsResult {
		fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
	}

	VnbxPlayerRelationsResult(boolean available, String player, boolean clanAvailable, Clan clan,
			boolean marriageAvailable, Marriage marriage) {
		this(available, null, player, clanAvailable, clan, marriageAvailable, marriage, Map.of());
	}

	static VnbxPlayerRelationsResult unavailable() {
		return unavailable(null, null);
	}

	static VnbxPlayerRelationsResult unavailable(String requestId, String player) {
		return new VnbxPlayerRelationsResult(false, requestId, player, false, null, false, null, Map.of());
	}

	boolean hasRelations() {
		return available && (clanAvailable || marriageAvailable);
	}

	record Clan(boolean inClan, String tag, String name, String rank, String leader) {
		Clan(boolean inClan, String tag, String name, String rank) {
			this(inClan, tag, name, rank, null);
		}
	}

	record Marriage(boolean married, String partnerUuid, String partnerName, String date, String surname) {
		Marriage(boolean married, String partnerUuid, String partnerName) {
			this(married, partnerUuid, partnerName, null, null);
		}
	}
}
