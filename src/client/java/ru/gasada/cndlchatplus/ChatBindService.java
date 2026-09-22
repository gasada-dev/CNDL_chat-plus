package ru.gasada.cndlchatplus;

import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
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
		Window window = minecraft.getWindow();
		return InputConstants.isKeyDown(window, bind.keyCode)
				&& (!bind.control || modifierDown(window, InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL))
				&& (!bind.shift || modifierDown(window, InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT))
				&& (!bind.alt || modifierDown(window, InputConstants.KEY_LALT, InputConstants.KEY_RALT));
	}

	private static boolean modifierDown(Window window, int left, int right) {
		return InputConstants.isKeyDown(window, left) || InputConstants.isKeyDown(window, right);
	}
}
