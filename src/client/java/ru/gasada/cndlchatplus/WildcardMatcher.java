package ru.gasada.cndlchatplus;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class WildcardMatcher {
	private static final int DEFAULT_CACHE_LIMIT = 512;
	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL;

	private final int cacheLimit;
	private final Map<CacheKey, CompiledWildcard> cache;

	public WildcardMatcher() {
		this(DEFAULT_CACHE_LIMIT);
	}

	WildcardMatcher(int cacheLimit) {
		if (cacheLimit < 1) {
			throw new IllegalArgumentException("cacheLimit must be positive");
		}
		this.cacheLimit = cacheLimit;
		this.cache = new LinkedHashMap<>(16, 0.75F, true);
	}

	public CompiledWildcard compile(String source, WildcardMatchMode mode) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(mode, "mode");
		CacheKey key = new CacheKey(source, mode);
		CompiledWildcard cached = cache.get(key);
		if (cached != null) {
			return cached;
		}

		CompiledWildcard compiled = compileUncached(source, mode);
		cache.put(key, compiled);
		if (cache.size() > cacheLimit) {
			CacheKey oldest = cache.keySet().iterator().next();
			cache.remove(oldest);
		}
		return compiled;
	}

	public boolean matches(String source, String text, WildcardMatchMode mode) {
		return compile(source, mode).matches(text);
	}

	int cachedPatternCount() {
		return cache.size();
	}

	public void clear() {
		cache.clear();
	}

	private static CompiledWildcard compileUncached(String source, WildcardMatchMode mode) {
		String normalizedSource = switch (mode) {
			case FULL_MATCH -> ChatTextNormalizer.normalizeForMatching(source);
			case CONTAINS_MATCH -> source.toLowerCase(Locale.ROOT).trim();
		};
		String regex = toRegex(normalizedSource, mode);
		return new CompiledWildcard(source, mode, Pattern.compile(regex, FLAGS));
	}

	private static String toRegex(String source, WildcardMatchMode mode) {
		StringBuilder regex = new StringBuilder();
		if (mode == WildcardMatchMode.FULL_MATCH) {
			regex.append('^');
		}
		int start = 0;
		for (int index = 0; index < source.length(); index++) {
			if (source.charAt(index) == '*') {
				regex.append(Pattern.quote(source.substring(start, index))).append(".*");
				start = index + 1;
			}
		}
		regex.append(Pattern.quote(source.substring(start)));
		if (mode == WildcardMatchMode.FULL_MATCH) {
			regex.append('$');
		}
		return regex.toString();
	}

	private record CacheKey(String source, WildcardMatchMode mode) {
	}
}
