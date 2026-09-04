package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ChatAlertService {
	private final BooleanSupplier enabled;
	private final ChatAlertRuleCompiler compiler = new ChatAlertRuleCompiler();
	private final ChatMessageSenderExtractor senderExtractor = new ChatMessageSenderExtractor();
	private volatile List<CompiledChatAlertRule> rules = List.of();

	public ChatAlertService(BooleanSupplier enabled, List<ChatAlertRule> rules) {
		this.enabled = enabled;
		reload(rules);
	}

	public void reload(List<ChatAlertRule> source) {
		rules = compiler.compile(source);
	}

	public ChatAlertDecision handle(String displayed, ChatTab channel) {
		return handle(displayed, channel, null);
	}

	public ChatAlertDecision handle(String displayed, ChatTab channel, CompiledParserSettings parsers) {
		if (!enabled.getAsBoolean() || displayed == null || channel == null) return ChatAlertDecision.none();
		String sanitized = ChatMessageTextSanitizer.stripSyntheticLabels(displayed);
		String text = senderExtractor.messageBody(sanitized, parsers);
		List<String> matched = new ArrayList<>();
		boolean hud = false;
		boolean sound = false;
		for (CompiledChatAlertRule rule : rules) {
			if (!rule.channel().matches(channel) || !rule.matcher().test(text)) continue;
			matched.add(rule.id());
			hud |= rule.hudEnabled();
			sound |= rule.soundEnabled();
		}
		return matched.isEmpty() ? ChatAlertDecision.none()
				: new ChatAlertDecision(matched, hud, sound);
	}
}
