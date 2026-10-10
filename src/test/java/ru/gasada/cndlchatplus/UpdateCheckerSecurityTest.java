package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

final class UpdateCheckerSecurityTest {
	private static final String VERSION = "0.4.4";
	private static final String URL_12111 = url("1.21.11");
	private static final String URL_262 = url("26.2");
	private static final String URL_263 = url("26.3");

	@Test
	void exactRepositoryJarsAndVersionAreAccepted() {
		assertEquals("https://api.github.com/repos/gasada-dev/CNDL_chat-plus/releases/latest",
				UpdateChecker.RELEASE_API_URL);
		assertEquals("https://github.com/gasada-dev/CNDL_chat-plus/blob/v0.4.4/UPDATE_NOTES.md",
				UpdateChecker.releaseNotesUrl(VERSION));
		assertNull(UpdateChecker.releaseNotesUrl("release"));
		assertTrue(UpdateChecker.validate(info(URL_12111, URL_262, URL_263)).valid());
	}

	@Test
	void eachTargetUrlHasStrictSchemeHostPathAndFilename() {
		assertEachTargetRejected(url -> url.replace("https:", "http:"));
		assertEachTargetRejected(url -> url.replace("github.com", "example.org"));
		assertEachTargetRejected(url -> url.replace("gasada-dev/CNDL_chat-plus", "other/repository"));
		assertEachTargetRejected(url -> url.replace("/v0.4.4/", "/v0.4.5/"));
		assertEachTargetRejected(url -> url.replace("CNDL_chat%2B-0.4.4", "CNDL_chat%2B-0.4.5"));
		assertEachTargetRejected(url -> url.replace("%2B", "+"));
		assertEachTargetRejected(url -> url.replace(".jar", ".zip"));
		assertEachTargetRejected(url -> url + "?download=1");
		assertEachTargetRejected(url -> url + "#fragment");
		assertEachTargetRejected(url -> url.replace("https://", "https://user@"));
		assertEachTargetRejected(url -> url.replace(".com/", ".com:444/"));
		assertRejected(URL_12111.replace("mc1.21.11", "mc26.2"), URL_262, URL_263);
		assertRejected(URL_12111, URL_262.replace("mc26.2", "mc26.3"), URL_263);
		assertRejected(URL_12111, URL_262, URL_263.replace("mc26.3", "mc26.2"));
		assertRejected(URL_12111.replace("mc1.21.11", "mc1.21.%31%31"), URL_262, URL_263);
	}

	@Test
	void manifestFieldsHaveStrictLimits() {
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				"release", URL_12111, URL_262, URL_263, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				"1." + "1".repeat(40), URL_12111, URL_262, URL_263, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, null, URL_262, URL_263, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, null, URL_263, "x")).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, URL_262, null, "x")).valid());
		assertTrue(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, URL_262, URL_263,
				"x".repeat(UpdateChecker.MAX_RELEASE_NOTES_BYTES))).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, URL_262, URL_263,
				"x".repeat(UpdateChecker.MAX_RELEASE_NOTES_BYTES + 1))).valid());
		assertFalse(UpdateChecker.validate(new UpdateChecker.UpdateInfo(
				VERSION, URL_12111, URL_262, URL_263,
				"я".repeat(UpdateChecker.MAX_RELEASE_NOTES_BYTES / 2 + 1))).valid());
	}

	@Test
	void latestReleaseRequiresAllThreeExactTargetAssets() {
		String asset12111 = asset("CNDL_chat+-0.4.4-mc1.21.11.jar", URL_12111);
		String asset262 = asset("CNDL_chat+-0.4.4-mc26.2.jar", URL_262);
		String asset263 = asset("CNDL_chat+-0.4.4-mc26.3.jar", URL_263);
		String json = releaseJson(
				asset("CNDL_chat+-0.4.4-sources.jar", "ignored"),
				asset262, asset12111, asset263);
		UpdateChecker.UpdateInfo parsed = UpdateChecker.parseRelease(json.getBytes(StandardCharsets.UTF_8));
		assertEquals(VERSION, parsed.version());
		assertEquals(URL_12111, parsed.minecraft12111DownloadUrl());
		assertEquals(URL_262, parsed.minecraft262DownloadUrl());
		assertEquals(URL_263, parsed.minecraft263DownloadUrl());
		assertEquals("ok", parsed.message());

		assertReleaseRejected(asset262, asset263);
		assertReleaseRejected(asset12111, asset263);
		assertReleaseRejected(asset12111, asset262);
		assertReleaseRejected(asset12111, asset12111, asset262, asset263);
		assertReleaseRejected(asset12111, asset262, asset262, asset263);
		assertReleaseRejected(asset12111, asset262, asset263, asset263);
		assertReleaseRejected(asset("CNDL_chat+-0.4.4-mc1.21.10.jar", URL_12111), asset262, asset263);
		assertReleaseRejected(asset12111, asset("CNDL_chat+-0.4.4-mc26.1.jar", URL_262), asset263);
		assertReleaseRejected(asset12111, asset262, asset("CNDL_chat+-0.4.4-mc26.4.jar", URL_263));
		assertReleaseRejected(asset("CNDL_chat+-0.4.5-mc1.21.11.jar", URL_12111), asset262, asset263);
		assertReleaseRejected(asset12111, asset("CNDL_chat+-0.4.5-mc26.2.jar", URL_262), asset263);
		assertReleaseRejected(asset12111, asset262, asset("CNDL_chat+-0.4.5-mc26.3.jar", URL_263));
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

	private static UpdateChecker.UpdateInfo info(String url12111, String url262, String url263) {
		return new UpdateChecker.UpdateInfo(VERSION, url12111, url262, url263, "Обновление");
	}

	private static void assertEachTargetRejected(UnaryOperator<String> mutation) {
		assertRejected(mutation.apply(URL_12111), URL_262, URL_263);
		assertRejected(URL_12111, mutation.apply(URL_262), URL_263);
		assertRejected(URL_12111, URL_262, mutation.apply(URL_263));
	}

	private static void assertRejected(String url12111, String url262, String url263) {
		assertFalse(UpdateChecker.validate(info(url12111, url262, url263)).valid());
	}

	private static void assertReleaseRejected(String... assets) {
		assertNull(UpdateChecker.parseRelease(releaseJson(assets).getBytes(StandardCharsets.UTF_8)));
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
