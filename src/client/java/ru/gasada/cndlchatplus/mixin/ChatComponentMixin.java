package ru.gasada.cndlchatplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.components.ChatComponent;
import ru.gasada.cndlchatplus.ChatTabFilterAccess;
import ru.gasada.cndlchatplus.CndlChatPlusClient;
import ru.gasada.cndlchatplus.ResponderConfig;
import ru.gasada.cndlchatplus.ThemeTokens;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatTabFilterAccess {
	// ponytail: require=0 — если Mojang сменит константу 100 в этих методах,
	// лимит молча останется vanilla вместо падения игры; проверка — docs/MANUAL_TESTS.md
	@ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"},
			constant = @Constant(intValue = 100), require = 0)
	private int gasada$chatHistoryLimit(int original) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return original;
		ResponderConfig config = CndlChatPlusClient.CONFIG;
		if (config == null || !Boolean.TRUE.equals(config.chatHistoryEnabled)) {
			return original;
		}
		return config.chatHistoryLimit;
	}

	@Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true, require = 1)
	private void gasada$themeChatWidth(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ThemeTokens.resolvedChatWidth(cir.getReturnValue()));
	}

	@Inject(method = "getHeight()I", at = @At("RETURN"), cancellable = true, require = 1)
	private void gasada$themeChatHeight(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(ThemeTokens.resolvedChatHeight(cir.getReturnValue()));
	}

	@ModifyConstant(method = "getLineHeight()I", constant = @Constant(doubleValue = 9.0, ordinal = 0), require = 1)
	private double gasada$themeChatLineHeight(double original) {
		return ThemeTokens.chatLineBaseHeight();
	}

	@Invoker("refreshTrimmedMessages")
	abstract void gasada$invokeRefreshTrimmed();

	@Invoker("getHeight")
	abstract int gasada$invokeGetHeight();

	@Invoker("getWidth")
	abstract int gasada$invokeGetWidth();

	@Invoker("getScale")
	abstract double gasada$invokeGetScale();

	@Invoker("getLineHeight")
	abstract int gasada$invokeGetLineHeight();

	@Override
	public void gasada$refreshTrimmed() {
		gasada$invokeRefreshTrimmed();
	}

	@Override
	public int gasada$chatHeight() {
		return gasada$invokeGetHeight();
	}

	@Override
	public int gasada$chatWidth() {
		return gasada$invokeGetWidth();
	}

	@Override
	public double gasada$chatScale() {
		return gasada$invokeGetScale();
	}

	@Override
	public int gasada$chatLineHeight() {
		return gasada$invokeGetLineHeight();
	}
}
