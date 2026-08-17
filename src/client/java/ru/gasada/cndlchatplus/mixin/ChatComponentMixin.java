package ru.gasada.cndlchatplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import net.minecraft.client.gui.components.ChatComponent;
import ru.gasada.cndlchatplus.ChatTabFilterAccess;
import ru.gasada.cndlchatplus.CndlChatPlusClient;
import ru.gasada.cndlchatplus.ResponderConfig;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatTabFilterAccess {
	// ponytail: require=0 — если Mojang сменит константу 100 в этих методах,
	// лимит молча останется vanilla вместо падения игры; проверка — docs/MANUAL_TESTS.md
	@ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"},
			constant = @Constant(intValue = 100), require = 0)
	private int gasada$chatHistoryLimit(int original) {
		ResponderConfig config = CndlChatPlusClient.CONFIG;
		if (config == null || !Boolean.TRUE.equals(config.chatHistoryEnabled)) {
			return original;
		}
		return config.chatHistoryLimit;
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
