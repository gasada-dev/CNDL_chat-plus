package ru.gasada.cndlchatplus;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;

final class ChatBindService {
	private final ResponderConfig config;
	private final OutgoingChatService outgoing;
	private final Set<ChatBind> heldBinds = new HashSet<>();
	private final Set<ChatBind> pressedBinds = new HashSet<>();

	ChatBindService(ResponderConfig config, OutgoingChatService outgoing) {
		this.config = config;
		this.outgoing = outgoing;
	}

	void tick(Minecraft minecraft) {
		if (ClientUi.currentScreen(minecraft) != null || minecraft.level == null) {
			resetRuntimeState();
			return;
		}
		pressedBinds.clear();
		for (ChatBind bind : config.chatBinds) {
			if (pressed(minecraft, bind)) pressedBinds.add(bind);
		}
		pressedBinds.removeIf(bind -> pressedBinds.stream().anyMatch(other -> other.includes(bind)));
		heldBinds.retainAll(pressedBinds);
		for (ChatBind bind : pressedBinds) {
			if (heldBinds.add(bind)) outgoing.sendCommand(bind.command);
		}
	}

	void resetRuntimeState() {
		heldBinds.clear();
		pressedBinds.clear();
	}

	private static boolean pressed(Minecraft minecraft, ChatBind bind) {
		return PlatformInput.keyDown(minecraft, bind.keyCode)
				&& (!bind.control || PlatformInput.controlDown(minecraft))
				&& (!bind.shift || PlatformInput.shiftDown(minecraft))
				&& (!bind.alt || PlatformInput.altDown(minecraft));
	}
}
