package ru.gasada.chatresponder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class UpdateChecker {
	static final String RELEASE_API_URL =
			"https://api.github.com/repos/gasada-dev/MineModChat-/releases/latest";
	static final int MAX_BODY_BYTES = 65_536;
	private static final int MAX_URL_LENGTH = 1_024;
	private static final int MAX_MESSAGE_LENGTH = 512;
	private static final String ALLOWED_DOWNLOAD_HOST = "github.com";
	private static final String ALLOWED_RELEASE_PATH = "/gasada-dev/MineModChat-/releases/download/";
	private static final String MINECRAFT_12111 = "1.21.11";
	private static final String MINECRAFT_262 = "26.2";
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(4))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	private final HttpClient client;
	private final AtomicReference<CheckState> state = new AtomicReference<>(CheckState.NOT_STARTED);
	private final AtomicReference<UpdateInfo> availableUpdate = new AtomicReference<>();
	private final String currentVersion;

	public UpdateChecker() {
		this(HTTP_CLIENT, currentModVersion());
	}

	UpdateChecker(HttpClient client, String currentVersion) {
		this.client = client;
		this.currentVersion = currentVersion;
	}

	private static String currentModVersion() {
		return FabricLoader.getInstance().getModContainer(GasadaChatResponderClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	private void start() {
		if (!state.compareAndSet(CheckState.NOT_STARTED, CheckState.CHECKING)) return;
		URI releaseUri = URI.create(RELEASE_API_URL);
		HttpRequest request = HttpRequest.newBuilder(releaseUri)
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "CNDL_chat+ Update Checker")
				.header("Cache-Control", "no-cache")
				.header("Accept", "application/vnd.github+json")
				.header("X-GitHub-Api-Version", "2022-11-28")
				.GET().build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
				.thenAccept(this::handleResponse)
				.exceptionally(exception -> {
					state.set(CheckState.FAILED);
					GasadaChatResponderClient.LOGGER.debug("Проверка обновления недоступна", exception);
					return null;
				});
	}

	private void handleResponse(HttpResponse<InputStream> response) {
		if (response.statusCode() != 200) {
			closeQuietly(response.body());
			state.set(CheckState.FAILED);
			return;
		}
		String contentType = response.headers().firstValue("Content-Type").orElse("");
		if (!isAllowedContentType(contentType)) {
			closeQuietly(response.body());
			state.set(CheckState.FAILED);
			return;
		}
		try (InputStream body = response.body()) {
			byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
			if (bytes.length > MAX_BODY_BYTES) {
				state.set(CheckState.FAILED);
				return;
			}
			UpdateInfo info = parseRelease(bytes);
			if (info != null && validate(info).valid()
					&& UpdateVersion.compare(info.version(), currentVersion) > 0) {
				availableUpdate.set(info);
				state.set(CheckState.AVAILABLE);
			} else {
				state.set(CheckState.NO_UPDATE);
			}
		} catch (IOException | RuntimeException exception) {
			state.set(CheckState.FAILED);
			GasadaChatResponderClient.LOGGER.debug("Некорректный ответ GitHub Release", exception);
		}
	}

	public void tick(Minecraft minecraft) {
		Screen currentScreen = ClientUi.currentScreen(minecraft);
		if (state.get() == CheckState.NOT_STARTED) {
			if (minecraft.getConnection() == null || currentScreen != null) return;
			start();
		}
		UpdateInfo info = availableUpdate.get();
		if (state.get() != CheckState.AVAILABLE || info == null
				|| minecraft.getConnection() == null || currentScreen != null) return;
		state.set(CheckState.SHOWN);
		ClientUi.setScreen(minecraft, new UpdateAvailableScreen(currentScreen, currentVersion, info));
	}

	static UpdateInfo parseRelease(byte[] bytes) {
		if (bytes == null || bytes.length > MAX_BODY_BYTES) return null;
		try {
			String json = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
			ReleaseDto dto = GSON.fromJson(json, ReleaseDto.class);
			if (dto == null || dto.tagName == null || !dto.tagName.startsWith("v")) return null;
			String version = dto.tagName.substring(1);
			if (!UpdateVersion.isStrictManifestVersion(version) || dto.assets == null) return null;
			String expected12111 = assetName(version, MINECRAFT_12111);
			String expected262 = assetName(version, MINECRAFT_262);
			String download12111 = null;
			String download262 = null;
			for (ReleaseAssetDto asset : dto.assets) {
				if (asset == null) continue;
				if (expected12111.equals(asset.name)) {
					if (download12111 != null || asset.browserDownloadUrl == null) return null;
					download12111 = asset.browserDownloadUrl;
				} else if (expected262.equals(asset.name)) {
					if (download262 != null || asset.browserDownloadUrl == null) return null;
					download262 = asset.browserDownloadUrl;
				}
			}
			return download12111 == null || download262 == null ? null
					: new UpdateInfo(version, download12111, download262, dto.body);
		} catch (CharacterCodingException | RuntimeException exception) {
			return null;
		}
	}

	static ValidationResult validate(UpdateInfo info) {
		if (info == null || !UpdateVersion.isStrictManifestVersion(info.version())) {
			return ValidationResult.failure("Некорректная версия обновления");
		}
		if (info.message() != null && info.message().length() > MAX_MESSAGE_LENGTH) {
			return ValidationResult.failure("Сообщение обновления слишком длинное");
		}
		ValidationResult first = validateDownloadUrl(info.version(), MINECRAFT_12111,
				info.minecraft12111DownloadUrl());
		if (!first.valid()) return first;
		return validateDownloadUrl(info.version(), MINECRAFT_262, info.minecraft262DownloadUrl());
	}

	private static ValidationResult validateDownloadUrl(String version, String minecraftVersion, String url) {
		if (url == null || url.length() > MAX_URL_LENGTH) {
			return ValidationResult.failure("Некорректная длина download URL");
		}
		try {
			URI uri = URI.create(url);
			String expectedFile = assetName(version, minecraftVersion);
			String expectedPath = ALLOWED_RELEASE_PATH + "v" + version + "/"
					+ expectedFile.replace("+", "%2B");
			boolean valid = "https".equalsIgnoreCase(uri.getScheme())
					&& ALLOWED_DOWNLOAD_HOST.equalsIgnoreCase(uri.getHost())
					&& (uri.getPort() == -1 || uri.getPort() == 443)
					&& uri.getRawUserInfo() == null
					&& uri.getRawFragment() == null
					&& uri.getRawQuery() == null
					&& expectedPath.equals(uri.getRawPath())
					&& uri.getRawPath().endsWith(".jar");
			return valid ? ValidationResult.success() : ValidationResult.failure("Download URL не разрешён");
		} catch (RuntimeException exception) {
			return ValidationResult.failure("Некорректный download URL");
		}
	}

	private static String assetName(String version, String minecraftVersion) {
		return "CNDL_chat+-" + version + "-mc" + minecraftVersion + ".jar";
	}

	static boolean isAllowedContentType(String contentType) {
		String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		return normalized.startsWith("application/json") || normalized.startsWith("text/plain");
	}

	CheckState state() { return state.get(); }

	private static void closeQuietly(InputStream stream) {
		try {
			stream.close();
		} catch (IOException exception) {
			GasadaChatResponderClient.LOGGER.debug("Не удалось закрыть update response body", exception);
		}
	}

	public enum CheckState { NOT_STARTED, CHECKING, NO_UPDATE, AVAILABLE, FAILED, SHOWN }
	public record UpdateInfo(String version, String minecraft12111DownloadUrl,
			String minecraft262DownloadUrl, String message) { }
	public record ValidationResult(boolean valid, String errorMessage) {
		private static ValidationResult success() { return new ValidationResult(true, ""); }
		private static ValidationResult failure(String error) { return new ValidationResult(false, error); }
	}
	private static final class ReleaseDto {
		@com.google.gson.annotations.SerializedName("tag_name")
		String tagName;
		String body;
		ReleaseAssetDto[] assets;
	}
	private static final class ReleaseAssetDto {
		String name;
		@com.google.gson.annotations.SerializedName("browser_download_url")
		String browserDownloadUrl;
	}
}
