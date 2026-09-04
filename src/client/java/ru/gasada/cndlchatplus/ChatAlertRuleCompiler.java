package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatAlertRuleCompiler {
	private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL;
	private final WildcardMatcher wildcardMatcher = new WildcardMatcher();

	public List<CompiledChatAlertRule> compile(List<ChatAlertRule> rules) {
		if (rules == null || rules.isEmpty()) return List.of();
		List<CompiledChatAlertRule> compiled = new ArrayList<>();
		for (ChatAlertRule source : rules) {
			if (source == null || !source.enabled || source.pattern == null || source.pattern.isBlank()) continue;
			try {
				ChatAlertRule rule = source.copy();
				compiled.add(new CompiledChatAlertRule(rule.id, rule.channel,
						rule.hudEnabled, rule.soundEnabled, matcher(rule.matchType, rule.pattern)));
			} catch (PatternSyntaxException exception) {
				CndlChatPlusClient.LOGGER.warn("Alert-правило {} пропущено: некорректный regex", source.id);
			}
		}
		return List.copyOf(compiled);
	}

	public String validationError(ChatAlertMatchType type, String pattern) {
		if (pattern == null || pattern.isBlank()) return "Шаблон не может быть пустым";
		if (pattern.trim().length() > ResponderConfig.MAX_CHAT_ALERT_PATTERN_LENGTH) {
			return "Шаблон слишком длинный";
		}
		if (type == ChatAlertMatchType.REGEX) {
			try {
				Pattern.compile(pattern, REGEX_FLAGS);
			} catch (PatternSyntaxException exception) {
				return "Некорректное регулярное выражение";
			}
		}
		return null;
	}

	private Predicate<String> matcher(ChatAlertMatchType type, String source) {
		return switch (type) {
			case TEXT -> {
				String normalized = ChatTextNormalizer.normalizeForMatching(source);
				yield text -> ChatTextNormalizer.normalizeForMatching(text).contains(normalized);
			}
			case WILDCARD -> wildcardMatcher.compile(source, WildcardMatchMode.CONTAINS_MATCH)::matches;
			case REGEX -> Pattern.compile(source, REGEX_FLAGS).asPredicate();
		};
	}
}
