package ru.gasada.cndlchatplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.gui.components.ChatComponent;
import ru.gasada.cndlchatplus.ThemeTokens;

@Mixin(ChatComponent.class)
public abstract class ChatBackgroundMixin {
	@ModifyConstant(method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
			constant = @Constant(intValue = 9, ordinal = 0), require = 1)
	private int gasada$chatLineHeight(int original) {
		return ThemeTokens.chatLineBaseHeight();
	}

	@ModifyArg(method = "method_75801(FLorg/joml/Matrix3x2f;)V",
			at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2f;translate(FF)Lorg/joml/Matrix3x2f;", ordinal = 0),
			index = 0, require = 1)
	private static float gasada$chatIndent(float original) {
		return ThemeTokens.chatIndent();
	}

	@ModifyArgs(
			method = "method_75802(IILnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IFLnet/minecraft/client/GuiMessage$Line;IF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 0),
			require = 1)
	private static void gasada$chatBackground(Args args) {
		int padding = ThemeTokens.chatIndent();
		args.set(0, -padding);
		args.set(2, args.<Integer>get(2) + padding - 4);
		args.set(4, ThemeTokens.themedChatBackground(args.get(4)));
	}
}
