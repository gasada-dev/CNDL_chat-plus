package ru.gasada.chatresponder;

import java.util.List;
import java.util.Optional;

public final class CompiledFilterSet {
	private final List<CompiledWildcard> mutedWords;

	private CompiledFilterSet(List<CompiledWildcard> mutedWords) {
		this.mutedWords = mutedWords;
	}

	public static CompiledFilterSet compile(ActiveTemplateSnapshot snapshot) {
		WildcardMatcher matcher = new WildcardMatcher();
		return new CompiledFilterSet(snapshot.mutedWords().stream()
				.map(source -> matcher.compile(source, WildcardMatchMode.CONTAINS_MATCH))
				.toList());
	}

	static boolean matchesAnyMutedPattern(List<String> mutedWords, String text) {
		WildcardMatcher matcher = new WildcardMatcher();
		for (String source : mutedWords) {
			if (matcher.matches(source, text, WildcardMatchMode.CONTAINS_MATCH)) {
				return true;
			}
		}
		return false;
	}

	public Optional<String> firstMutedWord(String text) {
		for (CompiledWildcard filter : mutedWords) {
			if (filter.matches(text)) {
				return Optional.of(filter.source());
			}
		}
		return Optional.empty();
	}

	int size() {
		return mutedWords.size();
	}
}
