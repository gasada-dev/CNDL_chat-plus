package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParserSettings {
	public String discordMarkerPattern = "";
	public String discordNamePattern = "";
	public String lastSeenPattern = "";
	public String inactivePattern = "";
	public String lookupEndPattern = "";
	public String lookupOutputPattern = "";
	public String timestampOnlyPattern = "";
	public List<String> replyCandidateSeparators = new ArrayList<>();
	public Map<String, String> playerInfoPatterns = new LinkedHashMap<>();
	public boolean playerInfoPatternsConfigured;
	public String marriageEntryPattern = "";
	public String marriagePagePattern = "";
	public String marriageEmptyPattern = "";

	public static final List<String> PLAYER_INFO_FIELDS = List.of(
			"Клан", "Ранг", "Статус", "КПД / KDR", "Убийств", "Нейтральных",
			"Смертей", "Дата вступления", "Прошлые кланы", "Тип убийства");

	public static ParserSettings vanillaBoxDefaults() {
		ParserSettings settings = new ParserSettings();
		settings.discordMarkerPattern =
				"(?iu)(?:\\(|\\[|<|\\{|«|‹|〈)\\s*discord\\s*(?:\\)|\\]|>|\\}|»|›|〉)";
		settings.discordNamePattern = "[\\p{L}\\p{N}_]{1,32}";
		settings.lastSeenPattern = "(?iu)Был\\s+(?:в\\s+сети|онлайн)\\s*:\\s*([^\\r\\n]+)";
		settings.inactivePattern = "(?iu)Неактивен\\s*:\\s*([^\\r\\n]+)";
		settings.lookupEndPattern = "(?iu)Тип\\s+убийства\\s*:";
		settings.lookupOutputPattern =
				"(?iu)(?:информация\\s+об\\s+игроке|профиль\\s+игрока|был\\s+(?:в\\s+сети|онлайн)|"
						+ "последн(?:ий|яя)\\s+(?:вход|активность)|ранг\\s*:|(?:кпд|kdr)\\s*:|убийств\\s*:|"
						+ "нейтральных\\s*:|смертей\\s*:|дата\\s+вступления\\s*:|прошлые\\s+кланы\\s*:|"
						+ "неактивен\\s*:|тип\\s+убийства\\s*:|статус\\s*:|клан\\s*:)";
		settings.timestampOnlyPattern = "\\s*\\[\\d{1,2}:\\d{2}(?::\\d{2})?]\\s*";
		settings.replyCandidateSeparators = new ArrayList<>(List.of(": ", "» ", "] ", "→ "));
		settings.playerInfoPatterns.put("Клан", "(?iu)Клан\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Ранг", "(?iu)Ранг\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Статус", "(?iu)Статус\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("КПД / KDR", "(?iu)(?:КПД|KDR)\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Убийств", "(?iu)Убийств\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Нейтральных", "(?iu)Нейтральных\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Смертей", "(?iu)Смертей\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Дата вступления", "(?iu)Дата\\s+вступления\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Прошлые кланы", "(?iu)Прошлые\\s+кланы\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatterns.put("Тип убийства", "(?iu)Тип\\s+убийства\\s*:\\s*([^\\r\\n]+)");
		settings.playerInfoPatternsConfigured = true;
		return settings;
	}

	public static void applyVanillaGameMarriageDefaults(ParserSettings settings) {
		settings.marriageEntryPattern =
				"(?iu)(?:^|\\s)([A-Za-z0-9_]{1,16})\\s*[❤♥♡]\\s*([A-Za-z0-9_]{1,16})(?:\\s|$)";
		settings.marriagePagePattern =
				"(?iu)страниц(?:а|е|у|ы)?\\s+(\\d+)\\s*/\\s*(\\d+)";
		settings.marriageEmptyPattern =
				"(?iu)нет\\s+(?:женатых|замужних)\\s+игроков";
	}

	public ParserSettings copy() {
		ParserSettings copy = new ParserSettings();
		copy.discordMarkerPattern = discordMarkerPattern;
		copy.discordNamePattern = discordNamePattern;
		copy.lastSeenPattern = lastSeenPattern;
		copy.inactivePattern = inactivePattern;
		copy.lookupEndPattern = lookupEndPattern;
		copy.lookupOutputPattern = lookupOutputPattern;
		copy.timestampOnlyPattern = timestampOnlyPattern;
		copy.replyCandidateSeparators = new ArrayList<>(
				replyCandidateSeparators == null ? List.of() : replyCandidateSeparators);
		copy.playerInfoPatterns = new LinkedHashMap<>(
				playerInfoPatterns == null ? Map.of() : playerInfoPatterns);
		copy.playerInfoPatternsConfigured = playerInfoPatternsConfigured;
		copy.marriageEntryPattern = marriageEntryPattern;
		copy.marriagePagePattern = marriagePagePattern;
		copy.marriageEmptyPattern = marriageEmptyPattern;
		return copy;
	}
}
