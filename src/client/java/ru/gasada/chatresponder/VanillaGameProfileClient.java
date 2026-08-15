package ru.gasada.chatresponder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class VanillaGameProfileClient {
	static final String HOST = "vanilla-game.ru";
	static final int MAX_BODY_BYTES = 65_536;
	static final int MAX_FIELD_LENGTH = 512;
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final HttpClient client;

	public VanillaGameProfileClient() {
		this(HttpClient.newBuilder().connectTimeout(TIMEOUT)
				.followRedirects(HttpClient.Redirect.NEVER).build());
	}

	VanillaGameProfileClient(HttpClient client) {
		this.client = client;
	}

	public CompletableFuture<FetchResult> fetch(String player) {
		PlayerNameValidator.ValidationResult validated = PlayerNameValidator.validate(player);
		if (!validated.valid()) {
			return CompletableFuture.completedFuture(FetchResult.failure(validated.errorMessage()));
		}
		URI uri = URI.create("https://" + HOST + "/main/player/" + player + "/page");
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT)
				.header("Accept", "application/json").GET().build();
		return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenApply(response -> parseResponse(response.statusCode(),
						response.headers().firstValue("Content-Type").orElse(""), response.body()))
				.exceptionally(error -> FetchResult.failure("Не удалось загрузить профиль игрока"));
	}

	static FetchResult parseResponse(int status, String contentType, byte[] body) {
		if (status != 200) return FetchResult.failure("Сервер профилей вернул HTTP " + status);
		String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		if (!normalizedType.startsWith("application/json")) {
			return FetchResult.failure("Сервер профилей вернул не JSON");
		}
		if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
			return FetchResult.failure("Некорректный размер ответа профиля");
		}
		try {
			String json = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(body)).toString();
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) return FetchResult.failure("Некорректный JSON профиля");
			return FetchResult.success(parseProfile(parsed.getAsJsonObject()));
		} catch (CharacterCodingException | RuntimeException error) {
			return FetchResult.failure("Некорректный JSON профиля");
		}
	}

	private static PlayerInfoProfile parseProfile(JsonObject root) {
		JsonObject clan = object(root, "clan");
		JsonObject marriage = object(root, "marry");
		List<PlayerInfoProfile.Building> buildings = new ArrayList<>();
		JsonArray array = array(root, "buildings");
		if (array != null) {
			for (JsonElement element : array) {
				if (!element.isJsonObject() || buildings.size() >= 50) continue;
				JsonObject building = element.getAsJsonObject();
				String title = string(building, "title");
				if (title != null) buildings.add(new PlayerInfoProfile.Building(
						title, string(building, "average_rating")));
			}
		}
		return new PlayerInfoProfile(
				string(root, "username"), string(root, "reg_date"), string(root, "last_login"),
				string(root, "about"), string(root, "city"), string(root, "telegram"),
				string(root, "vk"), string(root, "website"),
				clan == null ? null : new PlayerInfoProfile.Clan(
						first(clan, "tag", "color_tag"), string(clan, "name"),
						leaderName(clan), playerIsLeader(clan)),
				marriage == null ? null : new PlayerInfoProfile.Marriage(
						string(marriage, "partner"), string(marriage, "date"), string(marriage, "surname")),
				buildings);
	}

	private static String first(JsonObject object, String... names) {
		for (String name : names) {
			String value = string(object, name);
			if (value != null) return value;
		}
		return null;
	}

	private static String leaderName(JsonObject clan) {
		JsonElement value = clan.get("leader");
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? string(clan, "leader") : null;
	}

	private static boolean playerIsLeader(JsonObject clan) {
		JsonElement value = clan.get("leader");
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
				&& value.getAsBoolean();
	}

	private static String string(JsonObject object, String name) {
		if (object == null || !object.has(name) || object.get(name).isJsonNull()) return null;
		JsonElement value = object.get(name);
		if (!value.isJsonPrimitive()) return null;
		InputSanitizer.Result sanitized = InputSanitizer.validateAndTrim(
				value.getAsString(), MAX_FIELD_LENGTH, name);
		return sanitized.valid() ? sanitized.value() : null;
	}

	private static JsonObject object(JsonObject root, String name) {
		return root.has(name) && root.get(name).isJsonObject() ? root.getAsJsonObject(name) : null;
	}

	private static JsonArray array(JsonObject root, String name) {
		return root.has(name) && root.get(name).isJsonArray() ? root.getAsJsonArray(name) : null;
	}

	public record FetchResult(boolean success, PlayerInfoProfile profile, String errorMessage) {
		static FetchResult success(PlayerInfoProfile profile) { return new FetchResult(true, profile, ""); }
		static FetchResult failure(String message) { return new FetchResult(false, null, message); }
	}
}
