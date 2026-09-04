package ru.gasada.cndlchatplus;

import java.util.List;

public record ChatAlertDecision(List<String> matchedRuleIds, boolean hudEnabled, boolean soundEnabled) {
	private static final ChatAlertDecision NONE = new ChatAlertDecision(List.of(), false, false);

	public ChatAlertDecision {
		matchedRuleIds = List.copyOf(matchedRuleIds);
	}

	public static ChatAlertDecision none() {
		return NONE;
	}

	public boolean triggered() {
		return !matchedRuleIds.isEmpty();
	}
}
