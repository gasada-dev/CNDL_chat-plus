package ru.gasada.cndlchatplus;

import java.util.List;
import java.util.Optional;

public final class CompiledFilterSet {
	private final List<CompiledWildcard> mutedWords;

	private CompiledFilterSet(List<CompiledWildcard> mutedWords) {
		this.mutedWords = mutedWords;
	}

	public static CompiledFilterSet compile(List<String> mutedWords) {
		WildcardMatcher matcher = new WildcardMatcher();
		return new CompiledFilterSet(mutedWords.stream()
				.map(source -> matcher.compile(source, WildcardMatchMode.CONTAINS_MATCH))
				.toList());
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
