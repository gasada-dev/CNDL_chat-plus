package ru.gasada.cndlchatplus;

import java.util.function.Predicate;

record CompiledChatAlertRule(String id, ChatAlertChannel channel,
		boolean hudEnabled, boolean soundEnabled, Predicate<String> matcher) {
}
