package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

final class VanillaBoxConnectionGateTest {
	@ParameterizedTest
	@MethodSource("allowedAddresses")
	void allowsValidMultiplayerAddresses(String address, String normalized) {
		VanillaBoxConnectionGate.Decision decision = VanillaBoxConnectionGate.decide(address);

		assertTrue(decision.allowed());
		assertEquals(normalized, decision.normalizedAddress());
	}

	private static Stream<Arguments> allowedAddresses() {
		return Stream.of(
				Arguments.of("vanilla-box.ru", "vanilla-box.ru:25565"),
				Arguments.of("PLAY.EXAMPLE.ORG.:25566", "play.example.org:25566"),
				Arguments.of("127.0.0.1", "127.0.0.1:25565"),
				Arguments.of("[::1]", "[::1]:25565"),
				Arguments.of("[2001:DB8::1]:25566", "[2001:db8::1]:25566"),
				Arguments.of("пример.рф:00080", "xn--e1afmkfd.xn--p1ai:80"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			" ", "vanilla-box.ru\u0000evil", "*.vanilla-box.ru", ".vanilla-box.ru",
			"[::1", "[::1]evil", "[::1]:0",
			"vanilla-box.ru:", "vanilla-box.ru:0", "vanilla-box.ru:65536",
			"vanilla-box.ru:00000", "vanilla-box.ru:+80", "vanilla-box.ru:１２３",
			"vanilla-box.ru:999999999999999999999", "vanilla-box.ru\\evil",
			"https://vanilla-box.ru", "user@vanilla-box.ru", "vanilla-box.ru/path",
			"vanilla-box.ru?query", "vanilla-box.ru#fragment"
	})
	void rejectsMissingAndMalformedAddresses(String address) {
		VanillaBoxConnectionGate.Decision decision = VanillaBoxConnectionGate.decide(address);

		assertFalse(decision.allowed());
		assertNull(decision.normalizedAddress());
	}

	@Test
	void joinCachesNormalizedAddressAndInvalidJoinOrDisconnectClearsIt() {
		VanillaBoxConnectionGate gate = new VanillaBoxConnectionGate();

		assertFalse(gate.active());
		gate.join("PLAY.EXAMPLE.ORG.:25566");
		assertTrue(gate.active());
		assertEquals("play.example.org:25566", gate.state().normalizedAddress());

		gate.join("https://example.org");
		assertFalse(gate.active());
		assertNull(gate.state().normalizedAddress());

		gate.join("vanilla-box.ru");
		gate.disconnect();
		assertFalse(gate.active());
		assertNull(gate.state().normalizedAddress());
	}
}
