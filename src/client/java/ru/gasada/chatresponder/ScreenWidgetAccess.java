package ru.gasada.chatresponder;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

public interface ScreenWidgetAccess {
	<T extends GuiEventListener & Renderable & NarratableEntry> T gasada$addRenderableWidget(T widget);
}
