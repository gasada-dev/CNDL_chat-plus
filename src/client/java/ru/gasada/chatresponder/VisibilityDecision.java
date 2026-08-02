package ru.gasada.chatresponder;

public record VisibilityDecision(boolean visible, FilterReason reason, String matchedValue) {
	public static VisibilityDecision allow() {
		return new VisibilityDecision(true, FilterReason.VISIBLE, null);
	}

	public static VisibilityDecision hidden(FilterReason reason, String matchedValue) {
		return new VisibilityDecision(false, reason, matchedValue);
	}
}
