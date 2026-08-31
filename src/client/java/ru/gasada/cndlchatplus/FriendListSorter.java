package ru.gasada.cndlchatplus;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FriendListSorter {
	private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
	private static final Pattern RELATIVE = Pattern.compile(
			"(?iu)(\\d+)\\s*(секунд[а-я]*|минут[а-я]*|час[а-я]*|день|дня|дней|недел[а-я]*|месяц[а-я]*|год|года|лет)(?=\\s|$)");
	private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
			DateTimeFormatter.ofPattern("d.M.uuuu H:mm"),
			DateTimeFormatter.ofPattern("d.M.uuuu 'в' H:mm"),
			DateTimeFormatter.ofPattern("H:mm - d/M/uuuu"));
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.uuuu");

	private FriendListSorter() {
	}

	static List<String> sort(List<String> friends, Set<String> onlineFriends,
			Map<String, String> lastSeen, LocalDateTime now) {
		Set<String> normalizedOnline = new HashSet<>();
		onlineFriends.forEach(friend -> normalizedOnline.add(normalize(friend)));
		Map<String, String> normalizedLastSeen = new HashMap<>();
		lastSeen.forEach((friend, value) -> normalizedLastSeen.put(normalize(friend), value));
		Map<String, Long> timestamps = new HashMap<>();
		friends.forEach(friend -> timestamps.put(normalize(friend),
				parseTimestamp(normalizedLastSeen.get(normalize(friend)), now)));

		return friends.stream().sorted(Comparator.comparingLong((String friend) -> {
			String normalized = normalize(friend);
			return normalizedOnline.contains(normalized) ? Long.MAX_VALUE
					: timestamps.getOrDefault(normalized, Long.MIN_VALUE);
		}).reversed()).toList();
	}

	private static long parseTimestamp(String value, LocalDateTime now) {
		if (value == null || value.isBlank()) {
			return Long.MIN_VALUE;
		}
		String normalized = normalize(value).replace('ё', 'е');
		if (normalized.startsWith("сегодня")) {
			return epoch(LocalDateTime.of(now.toLocalDate(), timeFrom(normalized, now.toLocalTime())));
		}
		if (normalized.startsWith("вчера")) {
			return epoch(LocalDateTime.of(now.toLocalDate().minusDays(1), timeFrom(normalized, LocalTime.MIN)));
		}
		for (DateTimeFormatter format : DATE_TIME_FORMATS) {
			try {
				return epoch(LocalDateTime.parse(normalized, format));
			} catch (DateTimeParseException ignored) {
				// Try next supported server format.
			}
		}
		try {
			return epoch(LocalDate.parse(normalized, DATE_FORMAT).atStartOfDay());
		} catch (DateTimeParseException ignored) {
			// Relative format is checked below.
		}

		Matcher relative = RELATIVE.matcher(normalized);
		if (!relative.find()) {
			return Long.MIN_VALUE;
		}
		try {
			long amount = Long.parseLong(relative.group(1));
			String unit = relative.group(2);
			LocalDateTime timestamp;
			if (unit.startsWith("секунд")) timestamp = now.minusSeconds(amount);
			else if (unit.startsWith("минут")) timestamp = now.minusMinutes(amount);
			else if (unit.startsWith("час")) timestamp = now.minusHours(amount);
			else if (unit.startsWith("д")) timestamp = now.minusDays(amount);
			else if (unit.startsWith("недел")) timestamp = now.minusWeeks(amount);
			else if (unit.startsWith("месяц")) timestamp = now.minusMonths(amount);
			else timestamp = now.minusYears(amount);
			return epoch(timestamp);
		} catch (DateTimeException | NumberFormatException ignored) {
			return Long.MIN_VALUE;
		}
	}

	private static LocalTime timeFrom(String value, LocalTime fallback) {
		Matcher matcher = TIME.matcher(value);
		if (!matcher.find()) {
			return fallback;
		}
		try {
			return LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
		} catch (DateTimeException ignored) {
			return fallback;
		}
	}

	private static long epoch(LocalDateTime value) {
		return value.toInstant(ZoneOffset.UTC).toEpochMilli();
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).trim();
	}
}
