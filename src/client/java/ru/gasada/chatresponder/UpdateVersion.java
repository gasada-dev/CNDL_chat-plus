package ru.gasada.chatresponder;

public final class UpdateVersion {
	private UpdateVersion() {
	}

	public static int compare(String left, String right) {
		String[] leftParts = left.split("\\.");
		String[] rightParts = right.split("\\.");
		int length = Math.max(leftParts.length, rightParts.length);
		for (int index = 0; index < length; index++) {
			int leftValue = index < leftParts.length ? parsePart(leftParts[index]) : 0;
			int rightValue = index < rightParts.length ? parsePart(rightParts[index]) : 0;
			if (leftValue != rightValue) return Integer.compare(leftValue, rightValue);
		}
		return 0;
	}

	public static boolean isStrictManifestVersion(String value) {
		return value != null && value.length() <= 32 && value.matches("\\d+(?:\\.\\d+){1,3}");
	}

	private static int parsePart(String value) {
		try {
			return Integer.parseInt(value.replaceFirst("[^0-9].*$", ""));
		} catch (NumberFormatException exception) {
			return 0;
		}
	}
}
