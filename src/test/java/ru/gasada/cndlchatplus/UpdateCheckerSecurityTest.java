package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class UpdateCheckerSecurityTest {
	private static final String VERSION = "0.4.4";
	private static final String URL_12111 = url("1.21.11");
	private static final String URL_262 = url("26.2");

	@Test
	void exactRepositoryJarsAndVersionAreAccepted() {
		assertEquals("https://api.github.com/repos/gasada-dev/CNDL_chat-plus/releases/latest",
				UpdateChecker.RELEASE_API_URL);
		assertEquals("https://github.com/gasada-dev/CNDL_chat-plus/blob/v0.4.4/UPDATE_NOTES.md",
				UpdateChecker.releaseNotesUrl(VERSION));
		assertNull(UpdateChecker.releaseNotesUrl("release"));
		assertTrue(UpdateChecker.validate(info(URL_12111, URL_262)).valid());
	}

	@Test
	void eachTargetUrlHasStrictSchemeHostPathAndFilename() {
		assertRejected(URL_12111.replace("https:", "http:"), URL_262);
		assertRejected(URL_12111.replace("github.com", "example.org"), URL_262);
		assertRejected(URL_12111.replace("gasada-dev/CNDL_chat-plus", "other/repository"), URL_262);
		assertRejected(URL_12111.replace("CNDL_chat%2B-0.4.4", "CNDL_chat%2B-0.4.5"), URL_262);
		assertRejected(URL_12111.replace("mc1.21.11", "mc26.2"), URL_262);
		assertRejected(URL_12111.replace("%2B", "+"), URL_262);
		assertRejected(URL_12111.replace(".jar", ".zip"), URL_262);
		assertRejected(URL_12111 + "?download=1", URL_262);
		assertRejected(URL_12111 + "#fragment", URL_262);
		assertRejected(URL_12111.replace("https://", "https://user@"), URL_262);
		assertRejected(URL_12111.replace(".com/", ".com:444/"), URL_262);
		assertRejected(URL_12111.replace("mc1.21.11", "mc1.21.%31%31"), URL_262);
		assertRejected(URL_12111, URL_262.replace("mc26.2", "mc1.21.11"));
	}

	@Test
	void manifestFieldsHaveStrictLimits() {
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo("release", URL_12111, URL_262, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				"1." + "1".repeat(40), URL_12111, URL_262, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(VERSION, null, URL_262, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(VERSION, URL_12111, null, "x")).valid());
		assertTrue(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, URL_262, "x".repeat(UpdateChecker.MAX_RELEASE_NOTES_BYTES))).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, URL_262, "x".repeat(UpdateChecker.MAX_RELEASE_NOTES_BYTES + 1))).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, URL_262, "я".repeat(UpdateChecker.MAX_RELEASE_NOTES_BYTES / 2 + 1))).valid());
	}

	@Test
	void latestReleaseRequiresBothExactTargetAssets() {
		String json = releaseJson(
				asset("CNDL_chat+-0.4.4-sources.jar", "ignored"),
				asset("CNDL_chat+-0.4.4-mc26.2.jar", URL_262),
				asset("CNDL_chat+-0.4.4-mc1.21.11.jar", URL_12111));
		UpdateChecker.UpdateInfo parsed = UpdateChecker.parseRelease(json.getBytes(StandardCharsets.UTF_8));
		assertEquals(VERSION, parsed.version());
		assertEquals(URL_12111, parsed.minecraft12111DownloadUrl());
		assertEquals(URL_262, parsed.minecraft262DownloadUrl());
		assertEquals("ok", parsed.message());

		assertNull(UpdateChecker.parseRelease(releaseJson(
				asset("CNDL_chat+-0.4.4-mc1.21.11.jar", URL_12111)).getBytes(StandardCharsets.UTF_8)));
		assertNull(UpdateChecker.parseRelease(releaseJson(
				asset("CNDL_chat+-0.4.4-mc26.2.jar", URL_262)).getBytes(StandardCharsets.UTF_8)));
		assertNull(UpdateChecker.parseRelease(releaseJson(
				asset("CNDL_chat+-0.4.4-mc1.21.11.jar", URL_12111),
				asset("CNDL_chat+-0.4.4-mc1.21.11.jar", URL_12111),
				asset("CNDL_chat+-0.4.4-mc26.2.jar", URL_262)).getBytes(StandardCharsets.UTF_8)));
		assertNull(UpdateChecker.parseRelease(
				"{\"tag_name\":\"latest\",\"assets\":[]}".getBytes(StandardCharsets.UTF_8)));
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

	private static UpdateChecker.UpdateInfo info(String url12111, String url262) {
		return new UpdateChecker.UpdateInfo(VERSION, url12111, url262, "Обновление");
	}

	private static void assertRejected(String url12111, String url262) {
		assertFalse(UpdateChecker.validate(info(url12111, url262)).valid());
	}

	private static String url(String minecraftVersion) {
		return "https://github.com/gasada-dev/CNDL_chat-plus/releases/download/v" + VERSION
				+ "/CNDL_chat%2B-" + VERSION + "-mc" + minecraftVersion + ".jar";
	}

	private static String releaseJson(String... assets) {
		return "{\"tag_name\":\"v" + VERSION + "\",\"body\":\"ok\",\"assets\":["
				+ String.join(",", assets) + "]}";
	}

	private static String asset(String name, String url) {
		return "{\"name\":\"" + name + "\",\"browser_download_url\":\"" + url + "\"}";
	}
}
