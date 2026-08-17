package ru.gasada.cndlchatplus;

import java.util.HashSet;
import java.util.Set;

public final class ServerTemplateResolver {
	public Resolution resolve(RootConfig root, String serverAddress) {
		if (root == null) {
			return Resolution.none("Корневая конфигурация не задана");
		}
		root.sanitize();
		ServerAddressNormalizer.NormalizationResult normalized = ServerAddressNormalizer.normalize(serverAddress);
		if (!normalized.valid()) {
			return Resolution.none(normalized.errorMessage());
		}
		String address = normalized.normalizedAddress();
		Set<String> knownIds = knownTemplateIds(root);

		for (var binding : root.serverBindings.entrySet()) {
			ServerAddressNormalizer.NormalizationResult bindingAddress =
					ServerAddressNormalizer.normalize(binding.getKey());
			if (bindingAddress.valid() && address.equals(bindingAddress.normalizedAddress())
					&& knownIds.contains(binding.getValue())) {
				return new Resolution(binding.getValue(), ResolutionSource.EXPLICIT_BINDING, "");
			}
		}

		for (ServerTemplateInfo info : root.templates) {
			if (!knownIds.contains(info.id) || info.addressPatterns == null) {
				continue;
			}
			for (String source : info.addressPatterns) {
				AddressPatternValidator.ValidationResult pattern = AddressPatternValidator.validate(source);
				if (pattern.valid() && !pattern.wildcard() && address.equals(pattern.normalizedPattern())) {
					return new Resolution(info.id, ResolutionSource.EXACT_PATTERN, "");
				}
			}
		}

		String wildcardTemplate = null;
		int bestSpecificity = -1;
		for (ServerTemplateInfo info : root.templates) {
			if (!knownIds.contains(info.id) || info.addressPatterns == null) {
				continue;
			}
			for (String source : info.addressPatterns) {
				AddressPatternValidator.ValidationResult pattern = AddressPatternValidator.validate(source);
				if (!pattern.valid() || !pattern.wildcard() || !matchesWildcard(address, pattern.normalizedPattern())) {
					continue;
				}
				int specificity = pattern.normalizedPattern().length();
				if (specificity > bestSpecificity) {
					wildcardTemplate = info.id;
					bestSpecificity = specificity;
				}
			}
		}
		if (wildcardTemplate != null) {
			return new Resolution(wildcardTemplate, ResolutionSource.WILDCARD_PATTERN, "");
		}
		if (root.defaultTemplateId != null && knownIds.contains(root.defaultTemplateId)) {
			return new Resolution(root.defaultTemplateId, ResolutionSource.DEFAULT_TEMPLATE, "");
		}
		return Resolution.none("Для адреса не выбран шаблон");
	}

	public TemplateOperationResult<Void> bindExact(RootConfig root, String serverAddress, String templateId) {
		if (root == null || templateId == null || knownTemplateIds(root).contains(templateId) == false) {
			return TemplateOperationResult.failure("Неизвестный шаблон для привязки", null);
		}
		ServerAddressNormalizer.NormalizationResult normalized = ServerAddressNormalizer.normalize(serverAddress);
		if (!normalized.valid()) {
			return TemplateOperationResult.failure(normalized.errorMessage(), null);
		}
		root.serverBindings.put(normalized.normalizedAddress(), templateId);
		return TemplateOperationResult.success(null);
	}

	private static Set<String> knownTemplateIds(RootConfig root) {
		Set<String> result = new HashSet<>();
		for (ServerTemplateInfo info : root.templates) {
			if (info != null && info.id != null && !info.id.isBlank()) {
				result.add(info.id);
			}
		}
		return result;
	}

	private static boolean matchesWildcard(String address, String pattern) {
		String suffix = pattern.substring(1);
		return address.endsWith(suffix) && address.length() > suffix.length();
	}

	public enum ResolutionSource {
		EXPLICIT_BINDING,
		EXACT_PATTERN,
		WILDCARD_PATTERN,
		DEFAULT_TEMPLATE,
		NONE
	}

	public record Resolution(String templateId, ResolutionSource source, String errorMessage) {
		private static Resolution none(String errorMessage) {
			return new Resolution(null, ResolutionSource.NONE, errorMessage);
		}

		public boolean resolved() {
			return templateId != null;
		}
	}
}
