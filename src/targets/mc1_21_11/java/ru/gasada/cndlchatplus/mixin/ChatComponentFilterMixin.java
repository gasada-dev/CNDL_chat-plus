package ru.gasada.cndlchatplus.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import ru.gasada.cndlchatplus.ChatTabController;
import ru.gasada.cndlchatplus.ChatDuplicateAccess;
import ru.gasada.cndlchatplus.ChatMessageHitTest;
import ru.gasada.cndlchatplus.ChatMessageTarget;
import ru.gasada.cndlchatplus.ChatMessageUnderMouseAccess;
import ru.gasada.cndlchatplus.ChatTabFilterAccess;
import ru.gasada.cndlchatplus.CndlChatPlusClient;

@Mixin(ChatComponent.class)
public abstract class ChatComponentFilterMixin implements ChatMessageUnderMouseAccess, ChatDuplicateAccess {
	@Shadow @Final private List<GuiMessage> allMessages;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;
	@Shadow private int chatScrollbarPos;

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true, require = 0)
	private void gasada$filterByTab(GuiMessage message, CallbackInfo ci) {
		ChatTabController tabs = CndlChatPlusClient.CHAT_TABS;
		if (tabs != null && !tabs.isVisible(message.content(), null)
				|| CndlChatPlusClient.CHAT_SEARCH != null
				&& !CndlChatPlusClient.CHAT_SEARCH.matches(message.content().getString())) {
			ci.cancel();
		}
	}

	@Override
	public ChatMessageTarget gasada$messageUnderMouse(double mouseX, double mouseY, int screenHeight) {
		ChatTabFilterAccess access = (ChatTabFilterAccess) this;
		int index = ChatMessageHitTest.lineIndex(mouseX, mouseY, screenHeight, access.gasada$chatWidth(),
				access.gasada$chatHeight(), access.gasada$chatScale(), access.gasada$chatLineHeight(),
				chatScrollbarPos);
		if (index < 0 || index >= trimmedMessages.size()) return null;

		int groupStart = index;
		while (groupStart > 0 && !trimmedMessages.get(groupStart).endOfEntry()) groupStart--;
		int visibleOrdinal = 0;
		for (int line = 0; line <= groupStart; line++) {
			if (trimmedMessages.get(line).endOfEntry()) visibleOrdinal++;
		}
		for (GuiMessage message : allMessages) {
			if (gasada$isVisible(message) && --visibleOrdinal == 0) {
				boolean fromGame = CndlChatPlusClient.CHAT_TABS != null
						&& CndlChatPlusClient.CHAT_TABS.fromGame(message.content(), null);
				return new ChatMessageTarget(message.content(), fromGame);
			}
		}
		return null;
	}

	private boolean gasada$isVisible(GuiMessage message) {
		ChatTabController tabs = CndlChatPlusClient.CHAT_TABS;
		return (tabs == null || tabs.isVisible(message.content(), null))
				&& (CndlChatPlusClient.CHAT_SEARCH == null
				|| CndlChatPlusClient.CHAT_SEARCH.matches(message.content().getString()));
	}

	@Override
	public boolean gasada$replaceLatest(net.minecraft.network.chat.Component expected,
			net.minecraft.network.chat.Component replacement) {
		if (allMessages.isEmpty() || allMessages.getFirst().content() != expected) return false;
		GuiMessage previous = allMessages.getFirst();
		allMessages.set(0, new GuiMessage(previous.addedTime(), replacement,
				previous.signature(), previous.tag()));
		return true;
	}
}
