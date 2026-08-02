package ru.gasada.chatresponder;

import java.util.List;

public final class ReplyRuleMatcher {
	private final List<CompiledRule> rules;

	private ReplyRuleMatcher(List<CompiledRule> rules) {
		this.rules = rules;
	}

	public static ReplyRuleMatcher compile(List<ActiveTemplateSnapshot.RuleSnapshot> source) {
		WildcardMatcher matcher = new WildcardMatcher();
		return new ReplyRuleMatcher(source.stream()
				.map(rule -> new CompiledRule(rule,
						matcher.compile(rule.trigger(), WildcardMatchMode.FULL_MATCH)))
				.toList());
	}

	public ActiveTemplateSnapshot.RuleSnapshot findFirst(ChatChannel detectedChannel, List<String> candidates) {
		for (CompiledRule compiled : rules) {
			ActiveTemplateSnapshot.RuleSnapshot rule = compiled.rule();
			if (!rule.enabled() || rule.trigger().isBlank() || rule.response().isBlank()) {
				continue;
			}
			if (rule.channel() != ChatChannel.AUTO && rule.channel() != detectedChannel) {
				continue;
			}
			if (candidates.stream().anyMatch(compiled.wildcard()::matches)) {
				return rule;
			}
		}
		return null;
	}

	private record CompiledRule(ActiveTemplateSnapshot.RuleSnapshot rule, CompiledWildcard wildcard) {
	}
}
