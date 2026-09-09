package ru.gasada.cndlchatplus.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import ru.gasada.cndlchatplus.ChatTextSelectionAccess;

@Mixin(ChatComponent.class)
public abstract class ChatComponentSelectionMixin implements ChatTextSelectionAccess {
	@Shadow @Final private List<GuiMessage.Line> trimmedMessages;
	@Shadow private int chatScrollbarPos;

	@Override
	public List<String> gasada$selectionLines() {
		List<String> lines = new ArrayList<>(trimmedMessages.size());
		for (GuiMessage.Line line : trimmedMessages) {
			StringBuilder text = new StringBuilder();
			line.content().accept((index, style, codePoint) -> {
				text.appendCodePoint(codePoint);
				return true;
			});
			lines.add(text.toString());
		}
		return lines;
	}

	@Override
	public int gasada$selectionScrollPosition() {
		return chatScrollbarPos;
	}
}
