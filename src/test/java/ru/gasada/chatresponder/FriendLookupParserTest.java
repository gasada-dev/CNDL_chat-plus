package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class FriendLookupParserTest {
	@ParameterizedTest
	@ValueSource(strings = {
			"Был в сети: 01.08.2026 18:42",
			"Был онлайн: вчера в 21:15"
	})
	void recognizesLastSeenFormats(String line) {
		FriendLookupManager.LookupParseResult result = FriendLookupManager.parseMessage(line);

		assertEquals(FriendLookupManager.LookupMessageType.LAST_SEEN, result.type());
		assertEquals(line.substring(line.indexOf(':') + 1).trim(), result.value());
	}

	@Test
	void recognizesInactiveValue() {
		FriendLookupManager.LookupParseResult result =
				FriendLookupManager.parseMessage("Неактивен: 4 дня");

		assertEquals(FriendLookupManager.LookupMessageType.INACTIVE, result.type());
		assertEquals("4 дня", result.value());
	}

	@Test
	void capturesLookupEndLineAsAvailablePlayerInfo() {
		FriendLookupManager.LookupParseResult result =
				FriendLookupManager.parseMessage("Тип убийства: обычный");

		assertEquals(FriendLookupManager.LookupMessageType.PLAYER_INFO_FIELD, result.type());
		assertEquals("Тип убийства", result.fieldName());
		assertEquals("обычный", result.value());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Информация об игроке Steve",
			"Профиль игрока Steve"
	})
	void recognizesExistingServiceLines(String line) {
		assertEquals(FriendLookupManager.LookupMessageType.LOOKUP_OUTPUT,
				FriendLookupManager.parseMessage(line).type());
	}

	@Test
	void capturesNamedManualPlayerInfoFieldsBeforeLookupEnd() {
		FriendLookupManager.LookupParseResult clan = FriendLookupManager.parseMessage("Клан: Builders");
		FriendLookupManager.LookupParseResult end = FriendLookupManager.parseMessage("Тип убийства: обычный");

		assertEquals(FriendLookupManager.LookupMessageType.PLAYER_INFO_FIELD, clan.type());
		assertEquals("Клан", clan.fieldName());
		assertEquals("Builders", clan.value());
		assertEquals(FriendLookupManager.LookupMessageType.PLAYER_INFO_FIELD, end.type());
		assertEquals("Тип убийства", end.fieldName());
		assertEquals("обычный", end.value());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"Ранг: участник",
			"KDR: 1.25",
			"Убийств: 10",
			"Статус: онлайн",
			"Клан: Test"
	})
	void capturesVanillaBoxManualPlayerInfoFields(String line) {
		assertEquals(FriendLookupManager.LookupMessageType.PLAYER_INFO_FIELD,
				FriendLookupManager.parseMessage(line).type());
	}

	@Test
	void unrelatedOrdinaryMessageRemainsUnrelated() {
		assertEquals(FriendLookupManager.LookupMessageType.UNRELATED,
				FriendLookupManager.parseMessage("Игрок Steve пишет обычное сообщение").type());
	}

	@Test
	void lineWithoutExpectedPlayerRemainsUnrelated() {
		assertEquals(FriendLookupManager.LookupMessageType.UNRELATED,
				FriendLookupManager.parseMessage("Игрок Alex сейчас недоступен").type());
	}

	@Test
	void damagedLastSeenLineIsStillClassifiedAsLookupOutput() {
		assertEquals(FriendLookupManager.LookupMessageType.LOOKUP_OUTPUT,
				FriendLookupManager.parseMessage("Был онлайн").type());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "   ", "[9:05]", " [12:34:56] "})
	void blankAndTimestampOnlyLinesUseExistingHiddenClassification(String line) {
		assertEquals(FriendLookupManager.LookupMessageType.EMPTY_OR_TIMESTAMP,
				FriendLookupManager.parseMessage(line).type());
	}
}
