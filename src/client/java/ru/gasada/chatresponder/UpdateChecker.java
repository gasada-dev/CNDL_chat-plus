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
	static final String MANIFEST_URL =
			"https://raw.githubusercontent.com/gasada-dev/MineModChat-/main/version.json";
	static final int MAX_BODY_BYTES = 65_536;
	private static final int MAX_URL_LENGTH = 1_024;
	private static final int MAX_MESSAGE_LENGTH = 512;
	private static final String ALLOWED_DOWNLOAD_HOST = "raw.githubusercontent.com";
	private static final String ALLOWED_REPOSITORY_PATH = "/gasada-dev/MineModChat-/main/";
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
		URI manifestUri = URI.create(MANIFEST_URL + "?check=" + System.currentTimeMillis());
		HttpRequest request = HttpRequest.newBuilder(manifestUri)
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "CNDL_chat+ Update Checker")
				.header("Cache-Control", "no-cache")
				.header("Accept", "application/json, text/plain")
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
			UpdateInfo info = parseManifest(bytes);
			if (info != null && validate(info).valid()
					&& UpdateVersion.compare(info.version(), currentVersion) > 0) {
				availableUpdate.set(info);
				state.set(CheckState.AVAILABLE);
			} else {
				state.set(CheckState.NO_UPDATE);
			}
		} catch (IOException | RuntimeException exception) {
			state.set(CheckState.FAILED);
			GasadaChatResponderClient.LOGGER.debug("Некорректный манифест обновления", exception);
		}
	}

	public void tick(Minecraft minecraft) {
		Screen currentScreen = minecraft.gui.screen();
		if (state.get() == CheckState.NOT_STARTED) {
			if (minecraft.getConnection() == null || currentScreen != null) return;
			start();
		}
		UpdateInfo info = availableUpdate.get();
		if (state.get() != CheckState.AVAILABLE || info == null
				|| minecraft.getConnection() == null || currentScreen != null) return;
		state.set(CheckState.SHOWN);
		minecraft.gui.setScreen(new UpdateAvailableScreen(currentScreen, currentVersion, info));
	}

	static UpdateInfo parseManifest(byte[] bytes) {
		if (bytes == null || bytes.length > MAX_BODY_BYTES) return null;
		try {
			String json = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
			ManifestDto dto = GSON.fromJson(json, ManifestDto.class);
			return dto == null ? null : new UpdateInfo(dto.version, dto.downloadUrl, dto.message);
		} catch (CharacterCodingException | RuntimeException exception) {
			return null;
		}
	}

	static ValidationResult validate(UpdateInfo info) {
		if (info == null || !UpdateVersion.isStrictManifestVersion(info.version())) {
			return ValidationResult.failure("Некорректная версия обновления");
		}
		if (info.downloadUrl() == null || info.downloadUrl().length() > MAX_URL_LENGTH) {
			return ValidationResult.failure("Некорректная длина download URL");
		}
		if (info.message() != null && info.message().length() > MAX_MESSAGE_LENGTH) {
			return ValidationResult.failure("Сообщение обновления слишком длинное");
		}
		try {
			URI uri = URI.create(info.downloadUrl());
			String expectedFile = "CNDL_chat+-" + info.version() + ".jar";
			boolean valid = "https".equalsIgnoreCase(uri.getScheme())
					&& ALLOWED_DOWNLOAD_HOST.equalsIgnoreCase(uri.getHost())
					&& (uri.getPort() == -1 || uri.getPort() == 443)
					&& uri.getRawUserInfo() == null
					&& uri.getRawFragment() == null
					&& uri.getRawQuery() == null
					&& (ALLOWED_REPOSITORY_PATH + expectedFile).equals(uri.getPath())
					&& uri.getPath().endsWith(".jar");
			return valid ? ValidationResult.success() : ValidationResult.failure("Download URL не разрешён");
		} catch (RuntimeException exception) {
			return ValidationResult.failure("Некорректный download URL");
		}
	}

	static boolean isAllowedContentType(String contentType) {
		String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		return normalized.startsWith("application/json") || normalized.startsWith("text/plain");
	}

	static int compareVersions(String left, String right) {
		return UpdateVersion.compare(left, right);
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
	public record UpdateInfo(String version, String downloadUrl, String message) { }
	public record ValidationResult(boolean valid, String errorMessage) {
		private static ValidationResult success() { return new ValidationResult(true, ""); }
		private static ValidationResult failure(String error) { return new ValidationResult(false, error); }
	}
	private static final class ManifestDto {
		String version;
		String downloadUrl;
		String message;
	}
}
