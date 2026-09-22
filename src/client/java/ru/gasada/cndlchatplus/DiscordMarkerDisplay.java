package ru.gasada.cndlchatplus;

import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class DiscordMarkerDisplay {
	private static final Pattern MARKER = Pattern.compile("(?iu)\\(\\s*discord\\s*\\)");

	private DiscordMarkerDisplay() {
	}

	public static Component compact(Component component) {
		if (component == null || !MARKER.matcher(component.getString()).find()) return component;
		ComponentContents contents = component.getContents();
		MutableComponent copy = contents instanceof PlainTextContents literal
				? Component.literal(MARKER.matcher(literal.text()).replaceAll("(D)"))
				: component.plainCopy();
		copy.setStyle(component.getStyle());
		for (Component sibling : component.getSiblings()) {
			copy.append(compact(sibling));
		}
		return copy;
	}
}
