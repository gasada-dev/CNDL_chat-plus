package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class UpdateCheckerSecurityTest {
	private static final String VALID_URL = "https://github.com/gasada-dev/"
			+ "MineModChat-/releases/download/v0.4.4/CNDL_chat+-0.4.4.jar";

	@Test
	void exactRepositoryJarAndVersionAreAccepted() {
		UpdateChecker.UpdateInfo info = new UpdateChecker.UpdateInfo("0.4.4", VALID_URL, "Обновление");
		assertTrue(UpdateChecker.validate(info).valid());
	}

	@Test
	void schemeHostRepositoryFilenameAndVersionAreAllRestricted() {
		assertRejected(VALID_URL.replace("https:", "http:"));
		assertRejected(VALID_URL.replace("github.com", "example.org"));
		assertRejected(VALID_URL.replace("gasada-dev/MineModChat-", "other/repository"));
		assertRejected(VALID_URL.replace("0.4.4.jar", "0.4.5.jar"));
		assertRejected(VALID_URL.replace(".jar", ".zip"));
		assertRejected(VALID_URL + "?download=1");
		assertRejected(VALID_URL + "#fragment");
		assertRejected(VALID_URL.replace("https://", "https://user@"));
		assertRejected(VALID_URL.replace(".com/", ".com:444/"));
	}

	@Test
	void manifestFieldsHaveStrictLimits() {
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo("release", VALID_URL, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo("1." + "1".repeat(40), VALID_URL, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo("0.4.4", VALID_URL,
				"x".repeat(513))).valid());
	}

	@Test
	void latestReleaseParsingUsesTagAndMatchingJarAsset() {
		String json = "{\"tag_name\":\"v0.4.4\",\"body\":\"ok\",\"assets\":["
				+ "{\"name\":\"CNDL_chat+-0.4.4-sources.jar\",\"browser_download_url\":\"ignored\"},"
				+ "{\"name\":\"CNDL_chat+-0.4.4.jar\",\"browser_download_url\":\"" + VALID_URL + "\"}]}";
		UpdateChecker.UpdateInfo parsed = UpdateChecker.parseRelease(json.getBytes(StandardCharsets.UTF_8));
		assertEquals("0.4.4", parsed.version());
		assertEquals(VALID_URL, parsed.downloadUrl());
		assertEquals("ok", parsed.message());
		assertNull(UpdateChecker.parseRelease(
				"{\"tag_name\":\"v0.4.4\",\"assets\":[]}".getBytes(StandardCharsets.UTF_8)));
		assertNull(UpdateChecker.parseRelease(
				"{\"tag_name\":\"latest\",\"assets\":[]}".getBytes(StandardCharsets.UTF_8)));
		assertNull(UpdateChecker.parseRelease(
				"{\"tag_name\":\"0.4.4\",\"assets\":[]}".getBytes(StandardCharsets.UTF_8)));
		assertNull(UpdateChecker.parseRelease(new byte[] {(byte) 0xC3, (byte) 0x28}));
		assertNull(UpdateChecker.parseRelease(new byte[UpdateChecker.MAX_BODY_BYTES + 1]));
		assertNull(UpdateChecker.parseRelease("not json".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void onlyJsonAndPlainTextContentTypesAreAccepted() {
		assertTrue(UpdateChecker.isAllowedContentType("application/json; charset=utf-8"));
		assertTrue(UpdateChecker.isAllowedContentType("text/plain; charset=UTF-8"));
		assertFalse(UpdateChecker.isAllowedContentType("text/html"));
		assertFalse(UpdateChecker.isAllowedContentType(""));
	}

	private static void assertRejected(String url) {
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo("0.4.4", url, "x")).valid(), url);
	}
}
