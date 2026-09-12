package ru.gasada.cndlchatplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import ru.gasada.cndlchatplus.CndlChatPlusClient;
import ru.gasada.cndlchatplus.NicknameColorFix;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
			at = @At("RETURN"), cancellable = true)
	private void gasada$whitenBlackNickname(PlayerInfo playerInfo,
			CallbackInfoReturnable<Component> cir) {
		if (CndlChatPlusClient.CONNECTION_GATE.active() && CndlChatPlusClient.CONFIG != null
				&& CndlChatPlusClient.CONFIG.whitenBlackNames) {
			cir.setReturnValue(NicknameColorFix.whitenBlack(cir.getReturnValue()));
		}
	}
}
