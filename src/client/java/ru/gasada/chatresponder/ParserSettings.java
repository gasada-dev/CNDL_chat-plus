package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.List;

public final class ParserSettings {
	public String discordMarkerPattern = "";
	public String discordNamePattern = "";
	public String lastSeenPattern = "";
	public String inactivePattern = "";
	public String lookupEndPattern = "";
	public String lookupOutputPattern = "";
	public String timestampOnlyPattern = "";
	public List<String> replyCandidateSeparators = new ArrayList<>();

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
		return settings;
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
		return copy;
	}
}
