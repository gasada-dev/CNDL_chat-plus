package ru.gasada.cndlchatplus;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

final class ChatBindService {
	private final ResponderConfig config;
	private final OutgoingChatService outgoing;
	private final Set<Integer> heldKeyCodes = new HashSet<>();
	private final Set<Integer> pressedKeyCodes = new HashSet<>();

	ChatBindService(ResponderConfig config, OutgoingChatService outgoing) {
		this.config = config;
		this.outgoing = outgoing;
	}

	void tick(Minecraft minecraft) {
		if (ClientUi.currentScreen(minecraft) != null || minecraft.level == null) {
			resetRuntimeState();
			return;
		}
		pressedKeyCodes.clear();
		for (ChatBind bind : config.chatBinds) {
			if (InputConstants.isKeyDown(minecraft.getWindow(), bind.keyCode)) {
				pressedKeyCodes.add(bind.keyCode);
				if (!heldKeyCodes.contains(bind.keyCode)) {
					outgoing.sendCommand(bind.command);
				}
			}
		}
		heldKeyCodes.retainAll(pressedKeyCodes);
		heldKeyCodes.addAll(pressedKeyCodes);
	}

	void resetRuntimeState() {
		heldKeyCodes.clear();
		pressedKeyCodes.clear();
	}
}
