package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;

public final class ContextMenuBuilder {
	public List<ChatContextAction> build(ChatMessageSenderExtractor.Sender sender, Capabilities capabilities) {
		List<ChatContextAction> actions = new ArrayList<>();
		if (sender == null) return List.of(ChatContextAction.COPY_MESSAGE);
		actions.add(ChatContextAction.COPY_NICK);
		actions.add(ChatContextAction.COPY_MESSAGE);
		if (sender.discord() || capabilities == null || !capabilities.activeTemplate()) {
			return List.copyOf(actions);
		}
		if (capabilities.privateMessage()) actions.add(ChatContextAction.PRIVATE_MESSAGE);
		actions.add(ChatContextAction.ADD_FRIEND);
		if (capabilities.ignore()) actions.add(ChatContextAction.IGNORE);
		actions.add(ChatContextAction.PLAYER_INFO);
		if (capabilities.pay()) actions.add(ChatContextAction.PAY);
		if (capabilities.call()) actions.add(ChatContextAction.CALL);
		if (capabilities.mail()) actions.add(ChatContextAction.MAIL);
		return List.copyOf(actions);
	}

	public record Capabilities(boolean activeTemplate, boolean privateMessage, boolean ignore,
			boolean pay, boolean call, boolean mail) {
	}
}
