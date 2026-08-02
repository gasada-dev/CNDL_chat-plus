package ru.gasada.chatresponder;

public final class InputSanitizer {
	private InputSanitizer() {
	}

	public static Result validateAndTrim(String value, int maxLength, String label) {
		if (value == null) {
			return Result.failure(label + " не задан");
		}
		if (value.codePoints().anyMatch(InputSanitizer::isForbiddenControl)) {
			return Result.failure(label + " содержит управляющие символы");
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return Result.failure(label + " не должен быть пустым");
		}
		if (trimmed.length() > maxLength) {
			return Result.failure(label + " превышает допустимую длину " + maxLength);
		}
		return Result.success(trimmed);
	}

	private static boolean isForbiddenControl(int codePoint) {
		int type = Character.getType(codePoint);
		return Character.isISOControl(codePoint)
				|| type == Character.LINE_SEPARATOR
				|| type == Character.PARAGRAPH_SEPARATOR;
	}

	public record Result(boolean valid, String value, String errorMessage) {
		private static Result success(String value) {
			return new Result(true, value, "");
		}

		private static Result failure(String errorMessage) {
			return new Result(false, null, errorMessage);
		}
	}
}
