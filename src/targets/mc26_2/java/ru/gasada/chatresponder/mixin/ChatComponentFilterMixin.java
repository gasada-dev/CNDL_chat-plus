package ru.gasada.chatresponder.mixin;

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
import ru.gasada.chatresponder.ChatTabController;
import ru.gasada.chatresponder.ChatMessageHitTest;
import ru.gasada.chatresponder.ChatMessageTarget;
import ru.gasada.chatresponder.ChatMessageUnderMouseAccess;
import ru.gasada.chatresponder.ChatTabFilterAccess;
import ru.gasada.chatresponder.GasadaChatResponderClient;

@Mixin(ChatComponent.class)
public abstract class ChatComponentFilterMixin implements ChatMessageUnderMouseAccess {
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;
	@Shadow private int chatScrollbarPos;

	@Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true, require = 0)
	private void gasada$filterByTab(GuiMessage message, CallbackInfo ci) {
		ChatTabController tabs = GasadaChatResponderClient.CHAT_TABS;
		if (tabs != null && !tabs.isVisible(message.content(), message.source() != GuiMessageSource.PLAYER)
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
		GuiMessage message = trimmedMessages.get(index).parent();
		return new ChatMessageTarget(message.content(), message.source() != GuiMessageSource.PLAYER);
	}
}
