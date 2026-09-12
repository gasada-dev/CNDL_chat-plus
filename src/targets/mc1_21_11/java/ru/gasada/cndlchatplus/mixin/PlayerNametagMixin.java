package ru.gasada.cndlchatplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.gasada.cndlchatplus.CndlChatPlusClient;
import ru.gasada.cndlchatplus.NicknameColorFix;

@Mixin(EntityRenderer.class)
public abstract class PlayerNametagMixin {
	@Inject(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;",
			at = @At("RETURN"), cancellable = true)
	private void gasada$whitenBlackPlayerNametag(Entity entity,
			CallbackInfoReturnable<Component> cir) {
		if (CndlChatPlusClient.CONNECTION_GATE.active() && entity instanceof Player
				&& CndlChatPlusClient.CONFIG != null
				&& CndlChatPlusClient.CONFIG.whitenBlackNames) {
			cir.setReturnValue(NicknameColorFix.whitenBlack(cir.getReturnValue()));
		}
	}
}
