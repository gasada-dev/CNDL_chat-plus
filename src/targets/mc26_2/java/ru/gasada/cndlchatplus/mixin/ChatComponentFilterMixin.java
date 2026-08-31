package ru.gasada.cndlchatplus.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
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
		if (tabs != null && !tabs.isVisible(message.content(), message.source() != GuiMessageSource.PLAYER)
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
		GuiMessage message = trimmedMessages.get(index).parent();
		return new ChatMessageTarget(message.content(), message.source() != GuiMessageSource.PLAYER);
	}

	@Override
	public boolean gasada$replaceLatest(net.minecraft.network.chat.Component expected,
			net.minecraft.network.chat.Component replacement) {
		if (allMessages.isEmpty() || allMessages.getFirst().content() != expected) return false;
		GuiMessage previous = allMessages.getFirst();
		allMessages.set(0, new GuiMessage(previous.addedTime(), replacement, previous.signature(),
				previous.source(), previous.tag()));
		return true;
	}
}
