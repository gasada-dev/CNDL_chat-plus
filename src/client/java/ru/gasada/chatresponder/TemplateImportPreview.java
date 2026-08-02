package ru.gasada.chatresponder;

import java.util.List;
import java.util.Map;

public final class TemplateImportPreview {
	private final String sourceId;
	private final String targetId;
	private final ServerTemplate proposedTarget;
	private final Map<TemplateImportOptions.Category, String> summary;
	private final List<String> validationErrors;

	TemplateImportPreview(String sourceId, String targetId, ServerTemplate proposedTarget,
			Map<TemplateImportOptions.Category, String> summary, List<String> validationErrors) {
		this.sourceId = sourceId;
		this.targetId = targetId;
		this.proposedTarget = proposedTarget.deepCopy(proposedTarget.id, proposedTarget.name);
		this.summary = Map.copyOf(summary);
		this.validationErrors = List.copyOf(validationErrors);
	}

	public String sourceId() { return sourceId; }
	public String targetId() { return targetId; }
	public Map<TemplateImportOptions.Category, String> summary() { return summary; }
	public List<String> validationErrors() { return validationErrors; }
	public boolean valid() { return validationErrors.isEmpty(); }
	ServerTemplate proposedTargetCopy() {
		return proposedTarget.deepCopy(proposedTarget.id, proposedTarget.name);
	}
}
