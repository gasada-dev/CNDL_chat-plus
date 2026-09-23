package ru.gasada.cndlchatplus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class ThemeLoader {
	private ThemeLoader() {
	}

	static UiTheme parse(String json, UiTheme fallback) {
		try {
			JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) return fallback;
			return parse(root.getAsJsonObject(), fallback);
		} catch (RuntimeException error) {
			CndlChatPlusClient.LOGGER.warn("Не удалось прочитать тему", error);
			return fallback;
		}
	}

	static UiTheme load(Path file, UiTheme fallback) {
		try {
			UiTheme.Meta fallbackMeta = fallback.meta();
			UiTheme noIdFallback = new UiTheme(new UiTheme.Meta("", fallbackMeta.name(), fallbackMeta.author(),
					fallbackMeta.version()), fallback.colors(), fallback.geometry(), fallback.chat());
			UiTheme theme = parse(Files.readString(file, StandardCharsets.UTF_8), noIdFallback);
			if (theme == noIdFallback) return fallback;
			UiTheme.Meta meta = theme.meta();
			return meta.id().isEmpty() ? new UiTheme(new UiTheme.Meta(fileName(file), meta.name(), meta.author(),
					meta.version()), theme.colors(), theme.geometry(), theme.chat()) : theme;
		} catch (NoSuchFileException error) {
			CndlChatPlusClient.LOGGER.warn("Тема не найдена: {}", file.getFileName());
			return fallback;
		} catch (IOException error) {
			CndlChatPlusClient.LOGGER.warn("Не удалось загрузить тему {}", file, error);
			return fallback;
		}
	}

	static List<UiTheme> scan(Path directory) {
		List<UiTheme> themes = new ArrayList<>();
		try (var files = Files.list(directory)) {
			files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(file -> {
				UiTheme fallback = UiTheme.defaults();
				UiTheme theme = load(file, fallback);
				if (theme != fallback) themes.add(theme);
			});
		} catch (IOException error) {
			CndlChatPlusClient.LOGGER.warn("Не удалось просканировать темы {}", directory, error);
		}
		return themes;
	}

	private static UiTheme parse(JsonObject root, UiTheme fallback) {
		UiTheme.Meta meta = fallback.meta();
		UiTheme.Colors colors = fallback.colors();
		UiTheme.Geometry geometry = fallback.geometry();
		UiTheme.Chat chat = fallback.chat();
		JsonObject values = object(root, "meta");
		meta = new UiTheme.Meta(string(values, "id", meta.id()), string(values, "name", meta.name()),
				string(values, "author", meta.author()), integer(values, "version", meta.version()));
		values = object(root, "colors");
		colors = new UiTheme.Colors(color(values, "background", colors.background()), color(values, "surface", colors.surface()),
				color(values, "surfaceSecondary", colors.surfaceSecondary()), color(values, "hover", colors.hover()), color(values, "pressed", colors.pressed()),
				color(values, "border", colors.border()), color(values, "borderFocused", colors.borderFocused()), color(values, "accent", colors.accent()),
				color(values, "accentSoft", colors.accentSoft()), color(values, "text", colors.text()), color(values, "textMuted", colors.textMuted()),
				color(values, "success", colors.success()), color(values, "warning", colors.warning()), color(values, "danger", colors.danger()),
				color(values, "online", colors.online()), color(values, "hudSurface", colors.hudSurface()), color(values, "noticeSurface", colors.noticeSurface()),
				color(values, "marriageSurface", colors.marriageSurface()), color(values, "marriageBorder", colors.marriageBorder()), color(values, "marriageAccent", colors.marriageAccent()),
				color(values, "marriageText", colors.marriageText()), color(values, "chatBackground", colors.chatBackground()),
				color(values, "chatTabBackground", colors.chatTabBackground()), color(values, "chatTabBackgroundHover", colors.chatTabBackgroundHover()),
				color(values, "chatTabOutline", colors.chatTabOutline()), color(values, "chatTabText", colors.chatTabText()),
				color(values, "chatTabTextActive", colors.chatTabTextActive()), color(values, "chatBadge", colors.chatBadge()));
		values = object(root, "geometry");
		geometry = new UiTheme.Geometry(integer(values, "radiusSmall", geometry.radiusSmall()), integer(values, "radiusMedium", geometry.radiusMedium()),
				integer(values, "radiusLarge", geometry.radiusLarge()), integer(values, "borderWidth", geometry.borderWidth()),
				integer(values, "spacingSmall", geometry.spacingSmall()), integer(values, "spacingMedium", geometry.spacingMedium()), integer(values, "spacingLarge", geometry.spacingLarge()));
		values = object(root, "chat");
		chat = new UiTheme.Chat(integer(values, "padding", chat.padding()), integer(values, "messageSpacing", chat.messageSpacing()),
				integer(values, "width", chat.width()), integer(values, "height", chat.height()),
				doubleValue(values, "backgroundOpacity", chat.backgroundOpacity()), integer(values, "tabHeight", chat.tabHeight()),
				integer(values, "tabGap", chat.tabGap()), bool(values, "overrideBackground", chat.overrideBackground()));
		return new UiTheme(meta, colors, geometry, chat);
	}

	private static JsonObject object(JsonObject root, String key) {
		JsonElement value = root.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
	}

	private static String string(JsonObject object, String key, String fallback) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : fallback;
	}

	private static int integer(JsonObject object, String key, int fallback) {
		JsonElement value = object.get(key);
		try {
			return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsInt() : fallback;
		} catch (RuntimeException error) {
			return fallback;
		}
	}

	private static double doubleValue(JsonObject object, String key, double fallback) {
		JsonElement value = object.get(key);
		try {
			return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsDouble() : fallback;
		} catch (RuntimeException error) {
			return fallback;
		}
	}

	private static boolean bool(JsonObject object, String key, boolean fallback) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
	}

	private static int color(JsonObject object, String key, int fallback) {
		String value = string(object, key, null);
		if (value == null || !(value.length() == 7 || value.length() == 9) || value.charAt(0) != '#') return fallback;
		try {
			long parsed = Long.parseLong(value.substring(1), 16);
			return value.length() == 7 ? (int) (0xFF000000L | parsed) : (int) parsed;
		} catch (NumberFormatException error) {
			return fallback;
		}
	}

	private static String fileName(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - ".json".length());
	}
}
