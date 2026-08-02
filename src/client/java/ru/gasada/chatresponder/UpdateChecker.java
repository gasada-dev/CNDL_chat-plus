package ru.gasada.chatresponder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class UpdateChecker {
	private static final String MANIFEST_URL =
			"https://raw.githubusercontent.com/gasada-dev/MineModChat-/main/version.json";
	private static final String ALLOWED_DOWNLOAD_HOST = "raw.githubusercontent.com";
	private static final Gson GSON = new Gson();

	private final AtomicReference<UpdateInfo> availableUpdate = new AtomicReference<>();
	private final String currentVersion;
	private boolean checkStarted;
	private boolean shown;

	public UpdateChecker() {
		currentVersion = FabricLoader.getInstance().getModContainer(GasadaChatResponderClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	private void start() {
		checkStarted = true;
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(4))
				.build();
		URI manifestUri = URI.create(MANIFEST_URL + "?check=" + System.currentTimeMillis());
		HttpRequest request = HttpRequest.newBuilder(manifestUri)
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", "CNDL_chat+ Update Checker")
				.header("Cache-Control", "no-cache")
				.GET()
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(response -> {
					if (response.statusCode() != 200) {
						return;
					}
					try {
						UpdateInfo info = GSON.fromJson(response.body(), UpdateInfo.class);
						if (isValid(info) && compareVersions(info.version, currentVersion) > 0) {
							availableUpdate.set(info);
						}
					} catch (RuntimeException exception) {
						GasadaChatResponderClient.LOGGER.debug("Некорректный манифест обновления", exception);
					}
				})
				.exceptionally(exception -> {
					GasadaChatResponderClient.LOGGER.debug("Проверка обновления недоступна", exception);
					return null;
				});
	}

	public void tick(Minecraft minecraft) {
		Screen currentScreen = minecraft.gui.screen();
		if (!checkStarted) {
			// Первый кадр без GUI при активном подключении означает, что игрок вошёл на сервер.
			if (minecraft.getConnection() == null || currentScreen != null) {
				return;
			}
			start();
		}

		UpdateInfo info = availableUpdate.get();
		if (shown || info == null || minecraft.getConnection() == null) {
			return;
		}
		if (currentScreen != null) {
			return;
		}

		shown = true;
		minecraft.gui.setScreen(new UpdateAvailableScreen(currentScreen, currentVersion, info));
	}

	private static boolean isValid(UpdateInfo info) {
		if (info == null || info.version == null || info.downloadUrl == null
				|| !info.version.matches("\\d+(?:\\.\\d+){1,3}")) {
			return false;
		}
		try {
			URI uri = URI.create(info.downloadUrl);
			String expectedFile = "CNDL_chat+-" + info.version + ".jar";
			// URI#getPath уже декодирует %2B в '+'. URLDecoder здесь использовать нельзя:
			// он повторно превратит корректный '+' в пробел и отклонит настоящий JAR.
			String decodedPath = uri.getPath();
			return "https".equalsIgnoreCase(uri.getScheme())
					&& ALLOWED_DOWNLOAD_HOST.equalsIgnoreCase(uri.getHost())
					&& decodedPath.endsWith("/" + expectedFile);
		} catch (RuntimeException exception) {
			return false;
		}
	}

	static int compareVersions(String left, String right) {
		String[] leftParts = left.split("\\.");
		String[] rightParts = right.split("\\.");
		int length = Math.max(leftParts.length, rightParts.length);
		for (int index = 0; index < length; index++) {
			int leftValue = index < leftParts.length ? parsePart(leftParts[index]) : 0;
			int rightValue = index < rightParts.length ? parsePart(rightParts[index]) : 0;
			if (leftValue != rightValue) {
				return Integer.compare(leftValue, rightValue);
			}
		}
		return 0;
	}

	private static int parsePart(String value) {
		try {
			return Integer.parseInt(value.replaceFirst("[^0-9].*$", ""));
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	public static final class UpdateInfo {
		public String version;
		public String downloadUrl;
		public String message;
	}
}
