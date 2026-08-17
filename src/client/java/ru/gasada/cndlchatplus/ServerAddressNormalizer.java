package ru.gasada.cndlchatplus;

import java.net.IDN;
import java.util.Locale;

public final class ServerAddressNormalizer {
	public static final int DEFAULT_PORT = 25565;

	private ServerAddressNormalizer() {
	}

	public static NormalizationResult normalize(String value) {
		if (value == null) {
			return NormalizationResult.failure("Адрес сервера не задан");
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty() || containsControl(trimmed)) {
			return NormalizationResult.failure("Адрес сервера пуст или содержит управляющие символы");
		}

		String host;
		String portText = null;
		if (trimmed.startsWith("[")) {
			int end = trimmed.indexOf(']');
			if (end < 0) {
				return NormalizationResult.failure("Некорректный IPv6-адрес");
			}
			host = trimmed.substring(1, end);
			if (end + 1 < trimmed.length()) {
				if (trimmed.charAt(end + 1) != ':') {
					return NormalizationResult.failure("Некорректный адрес сервера");
				}
				portText = trimmed.substring(end + 2);
			}
		} else {
			int firstColon = trimmed.indexOf(':');
			int lastColon = trimmed.lastIndexOf(':');
			if (firstColon >= 0 && firstColon == lastColon) {
				host = trimmed.substring(0, firstColon);
				portText = trimmed.substring(firstColon + 1);
			} else {
				host = trimmed;
			}
		}

		host = host.trim();
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		if (host.isEmpty() || host.contains("/") || host.contains("?") || host.contains("#")
				|| host.contains("@") || host.contains(" ")) {
			return NormalizationResult.failure("Некорректное имя сервера");
		}

		int port = DEFAULT_PORT;
		if (portText != null) {
			try {
				port = Integer.parseInt(portText);
			} catch (NumberFormatException error) {
				return NormalizationResult.failure("Некорректный порт сервера");
			}
			if (port < 1 || port > 65535) {
				return NormalizationResult.failure("Порт сервера должен быть от 1 до 65535");
			}
		}

		String normalizedHost;
		try {
			normalizedHost = host.contains(":")
					? host.toLowerCase(Locale.ROOT)
					: IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
		} catch (IllegalArgumentException error) {
			return NormalizationResult.failure("Некорректное имя сервера");
		}
		String normalized = normalizedHost.contains(":")
				? "[" + normalizedHost + "]:" + port
				: normalizedHost + ":" + port;
		return NormalizationResult.success(normalized);
	}

	private static boolean containsControl(String value) {
		return value.codePoints().anyMatch(Character::isISOControl);
	}

	public record NormalizationResult(boolean valid, String normalizedAddress, String errorMessage) {
		private static NormalizationResult success(String normalizedAddress) {
			return new NormalizationResult(true, normalizedAddress, "");
		}

		private static NormalizationResult failure(String errorMessage) {
			return new NormalizationResult(false, null, errorMessage);
		}
	}
}
