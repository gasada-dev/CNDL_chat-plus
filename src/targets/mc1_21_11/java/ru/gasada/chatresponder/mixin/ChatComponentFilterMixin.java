package ru.gasada.chatresponder.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import ru.gasada.chatresponder.ChatTabController;
import ru.gasada.chatresponder.ChatMessageHitTest;
import ru.gasada.chatresponder.ChatMessageTarget;
import ru.gasada.chatresponder.ChatMessageUnderMouseAccess;
import ru.gasada.chatresponder.ChatTabFilterAccess;
import ru.gasada.chatresponder.GasadaChatResponderClient;

@Mixin(ChatComponent.class)
public abstract class ChatComponentFilterMixin implements ChatMessageUnderMouseAccess {
	@Shadow @Final private List<GuiMessage> allMessages;
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;
	@Shadow private int chatScrollbarPos;

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true, require = 0)
	private void gasada$filterByTab(GuiMessage message, CallbackInfo ci) {
		ChatTabController tabs = GasadaChatResponderClient.CHAT_TABS;
		if (tabs != null && !tabs.isVisible(message.content(), null)
				|| GasadaChatResponderClient.CHAT_SEARCH != null
				&& !GasadaChatResponderClient.CHAT_SEARCH.matches(message.content().getString())) {
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
				boolean fromGame = GasadaChatResponderClient.CHAT_TABS != null
						&& GasadaChatResponderClient.CHAT_TABS.fromGame(message.content(), null);
				return new ChatMessageTarget(message.content(), fromGame);
			}
		}
		return null;
	}

	private boolean gasada$isVisible(GuiMessage message) {
		ChatTabController tabs = GasadaChatResponderClient.CHAT_TABS;
		return (tabs == null || tabs.isVisible(message.content(), null))
				&& (GasadaChatResponderClient.CHAT_SEARCH == null
				|| GasadaChatResponderClient.CHAT_SEARCH.matches(message.content().getString()));
	}
}
