package ru.gasada.cndlchatplus.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import ru.gasada.cndlchatplus.ChatDuplicateCollapser;
import ru.gasada.cndlchatplus.ChatTimestamps;
import ru.gasada.cndlchatplus.CndlChatPlusClient;

@Mixin(ChatComponent.class)
public abstract class ChatTimestampMixin {
	@Shadow @Final private List<GuiMessage> allMessages;

	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private Component gasada$timestampPrefix(Component message) {
		ChatTimestamps timestamps = CndlChatPlusClient.CHAT_TIMESTAMPS;
		return timestamps == null ? message : timestamps.apply(message);
	}

	@Inject(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("RETURN"), require = 0)
	private void gasada$observeDisplayed(CallbackInfo ci) {
		ChatDuplicateCollapser duplicates = CndlChatPlusClient.CHAT_DUPLICATES;
		if (duplicates != null && !allMessages.isEmpty()) {
			duplicates.observeDisplayed(allMessages.getFirst().content());
		}
	}
}
