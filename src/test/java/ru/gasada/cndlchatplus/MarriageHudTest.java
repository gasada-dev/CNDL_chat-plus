package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

final class MarriageHudTest {
	@Test
	void marksOfflinePartnerInGrayAndLeavesOnlinePartnerUnchanged() {
		MarriageHud.PartnerStatus offline = MarriageHud.partnerStatus("Partner_1", Set.of("Other_1"));
		MarriageHud.PartnerStatus online = MarriageHud.partnerStatus("Partner_1", Set.of("partner_1"));

		assertEquals("оффлайн", offline.text());
		assertEquals(ThemeTokens.textMuted(), offline.color());
		assertEquals("", online.text());
	}
}
