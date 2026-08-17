package ru.gasada.cndlchatplus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import ru.gasada.cndlchatplus.ScreenWidgetAccess;

@Mixin(Screen.class)
public abstract class ScreenMixin implements ScreenWidgetAccess {
	@Override
	@Invoker("addRenderableWidget")
	public abstract <T extends GuiEventListener & Renderable & NarratableEntry> T gasada$addRenderableWidget(T widget);
}
