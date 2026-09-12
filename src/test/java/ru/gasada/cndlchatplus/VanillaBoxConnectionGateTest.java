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
	void allowsOnlyApexAndTrueSubdomains(String address, String normalized) {
		VanillaBoxConnectionGate.Decision decision = VanillaBoxConnectionGate.decide(address);

		assertTrue(decision.allowed());
		assertEquals(normalized, decision.normalizedAddress());
	}

	private static Stream<Arguments> allowedAddresses() {
		return Stream.of(
				Arguments.of("vanilla-box.ru", "vanilla-box.ru:25565"),
				Arguments.of("VANILLA-BOX.RU.", "vanilla-box.ru:25565"),
				Arguments.of("mc.vanilla-box.ru:1", "mc.vanilla-box.ru:1"),
				Arguments.of("deep.mc.vanilla-box.ru.:65535", "deep.mc.vanilla-box.ru:65535"),
				Arguments.of("deep。mc.vanilla-box.ru:00065535", "deep.mc.vanilla-box.ru:65535"),
				Arguments.of("пример.vanilla-box.ru:00080", "xn--e1afmkfd.vanilla-box.ru:80"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			" ", " vanilla-box.ru", "vanilla-box.ru ", "vanilla-box.ru\u0000evil",
			"example.org", "vanilla-box.ru.example.org", "notvanilla-box.ru", "vanilla-box.ru.evil",
			"*.vanilla-box.ru", "vanilla-bоx.ru", "127.0.0.1", "[::1]", "::1",
			"vanilla-box.ru..", "vanilla-box.ru..:25565", "vanilla-box.ru。", ".vanilla-box.ru",
			"vanilla-box.ru:", "vanilla-box.ru:0", "vanilla-box.ru:65536",
			"vanilla-box.ru:00000", "vanilla-box.ru:+80", "vanilla-box.ru:１２３",
			"vanilla-box.ru:999999999999999999999", "vanilla-box.ru\\evil",
			"https://vanilla-box.ru", "user@vanilla-box.ru", "vanilla-box.ru/path",
			"vanilla-box.ru?query", "vanilla-box.ru#fragment"
	})
	void rejectsMalformedAndNonTargetAddresses(String address) {
		VanillaBoxConnectionGate.Decision decision = VanillaBoxConnectionGate.decide(address);

		assertFalse(decision.allowed());
		assertNull(decision.normalizedAddress());
	}

	@Test
	void joinCachesAllowedAddressAndDeniedJoinOrDisconnectClearsIt() {
		VanillaBoxConnectionGate gate = new VanillaBoxConnectionGate();

		assertFalse(gate.active());
		gate.join("MC.VANILLA-BOX.RU.:25566");
		assertTrue(gate.active());
		assertEquals("mc.vanilla-box.ru:25566", gate.state().normalizedAddress());

		gate.join("vanilla-box.ru.evil");
		assertFalse(gate.active());
		assertNull(gate.state().normalizedAddress());

		gate.join("vanilla-box.ru");
		gate.disconnect();
		assertFalse(gate.active());
		assertNull(gate.state().normalizedAddress());
	}
}
