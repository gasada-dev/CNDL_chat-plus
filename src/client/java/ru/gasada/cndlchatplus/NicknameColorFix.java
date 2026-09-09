package ru.gasada.cndlchatplus;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class NicknameColorFix {
	private static final int BLACK = 0x000000;
	private static final int WHITE = 0xFFFFFF;

	private NicknameColorFix() {
	}

	public static Component whitenBlack(Component component) {
		if (component == null) return Component.empty();

		ComponentContents contents = component.getContents();
		MutableComponent copy = contents instanceof PlainTextContents literal
				? Component.literal(literal.text().replace("§0", "§f"))
				: component.plainCopy();
		copy.setStyle(whitenStyle(component.getStyle()));
		for (Component sibling : component.getSiblings()) {
			copy.append(whitenBlack(sibling));
		}
		return copy;
	}

	private static Style whitenStyle(Style style) {
		TextColor color = style.getColor();
		return color != null && color.getValue() == BLACK ? style.withColor(WHITE) : style;
	}
}
