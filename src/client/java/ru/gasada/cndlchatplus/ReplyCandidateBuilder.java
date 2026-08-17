package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ReplyCandidateBuilder {
	public List<String> build(String content, String displayed, ChatChannel channel,
			ActiveTemplateSnapshot template, CompiledParserSettings parsers) {
		Set<String> candidates = new LinkedHashSet<>();
		addCandidate(candidates, content);
		addCandidate(candidates, displayed);
		if (channel == ChatChannel.GLOBAL && !template.globalPrefix().isBlank()) {
			addAfterPrefix(candidates, content, template.globalPrefix());
		}
		if (channel == ChatChannel.CLAN && !template.clanReplyPrefix().isBlank()) {
			addAfterPrefix(candidates, content, template.clanReplyPrefix());
		}
		for (String separator : parsers.replyCandidateSeparators()) {
			addTextAfterLastSeparator(candidates, displayed, separator);
		}
		return new ArrayList<>(candidates);
	}

	private static void addAfterPrefix(Set<String> candidates, String text, String rawPrefix) {
		String normalized = normalize(text);
		String prefix = normalize(rawPrefix);
		if (normalized.startsWith(prefix)) {
			addCandidate(candidates, normalized.substring(prefix.length()));
		}
	}

	private static void addCandidate(Set<String> candidates, String text) {
		String normalized = normalize(text);
		if (!normalized.isEmpty()) {
			candidates.add(normalized);
		}
	}

	private static void addTextAfterLastSeparator(Set<String> candidates, String text, String separator) {
		int index = text.lastIndexOf(separator);
		if (index >= 0 && index + separator.length() < text.length()) {
			addCandidate(candidates, text.substring(index + separator.length()));
		}
	}

	private static String normalize(String value) {
		return ChatTextNormalizer.normalizeForMatching(value);
	}
}
