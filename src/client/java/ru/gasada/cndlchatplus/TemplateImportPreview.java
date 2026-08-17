package ru.gasada.cndlchatplus;

import java.util.List;
import java.util.Map;

public final class TemplateImportPreview {
	private final ServerTemplate proposedTarget;
	private final Map<TemplateImportOptions.Category, String> summary;
	private final List<String> validationErrors;

	TemplateImportPreview(ServerTemplate proposedTarget,
			Map<TemplateImportOptions.Category, String> summary, List<String> validationErrors) {
		this.proposedTarget = proposedTarget.deepCopy(proposedTarget.id, proposedTarget.name);
		this.summary = Map.copyOf(summary);
		this.validationErrors = List.copyOf(validationErrors);
	}

	public Map<TemplateImportOptions.Category, String> summary() { return summary; }
	public List<String> validationErrors() { return validationErrors; }
	public boolean valid() { return validationErrors.isEmpty(); }
	ServerTemplate proposedTargetCopy() {
		return proposedTarget.deepCopy(proposedTarget.id, proposedTarget.name);
	}
}
