package ru.gasada.cndlchatplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import ru.gasada.cndlchatplus.ChatTimestamps;
import ru.gasada.cndlchatplus.CndlChatPlusClient;

@Mixin(ChatComponent.class)
public abstract class ChatTimestampMixin {
	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private Component gasada$timestampPrefix(Component message) {
		ChatTimestamps timestamps = CndlChatPlusClient.CHAT_TIMESTAMPS;
		return timestamps == null ? message : timestamps.apply(message);
	}
}
