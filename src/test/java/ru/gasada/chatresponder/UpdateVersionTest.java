package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UpdateVersionTest {
	@Test
	void newerPatchVersionComparesGreater() {
		assertTrue(UpdateChecker.compareVersions("0.4.4", "0.4.3") > 0);
	}

	@Test
	void sameVersionComparesEqual() {
		assertEquals(0, UpdateChecker.compareVersions("0.4.3", "0.4.3"));
	}

	@Test
	void trailingZeroDoesNotChangeVersion() {
		assertEquals(0, UpdateChecker.compareVersions("0.4.3", "0.4.3.0"));
	}

	@Test
	void numericPartsAreNotComparedLexicographically() {
		assertTrue(UpdateChecker.compareVersions("0.4.10", "0.4.9") > 0);
	}

	@Test
	void nonNumericVersionIsTreatedAsZero() {
		assertEquals(0, UpdateChecker.compareVersions("release", "0.0.0"));
	}

	@Test
	void suffixAfterNumericPrefixIsIgnored() {
		assertEquals(0, UpdateChecker.compareVersions("0.4.3-beta", "0.4.3"));
	}

	@Test
	void nonNumericPartIsTreatedAsZero() {
		assertEquals(0, UpdateChecker.compareVersions("0.4.invalid", "0.4.0"));
	}

	@Test
	void overflowingNumericPartIsTreatedAsZero() {
		assertTrue(UpdateChecker.compareVersions("999999999999999999999.0", "1.0") < 0);
	}
}
