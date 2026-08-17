package ru.gasada.cndlchatplus;

import net.minecraft.client.gui.Font;

public final class CreditRenderer {
	private static final String PREFIX = "create by ";
	private static final String NAME = "CNDL";
	private static final float[] STOPS = {8.333333F, 18.115942F, 66.666667F, 100.0F};
	private static final int[] COLORS = {0xE0DCFF, 0xD5A6FD, 0xB76EF5, 0xA242F3};

	private CreditRenderer() {
	}

	public static void draw(CompatGraphics graphics, Font font, int x, int y, int prefixColor) {
		graphics.text(font, PREFIX, x, y, prefixColor);
		int currentX = x + font.width(PREFIX);

		for (int index = 0; index < NAME.length(); index++) {
			String character = NAME.substring(index, index + 1);
			float position = ((index + 0.5F) / NAME.length()) * 100.0F;
			graphics.text(font, character, currentX, y, 0xFF000000 | colorAt(position));
			currentX += font.width(character);
		}
	}

	private static int colorAt(float position) {
		if (position <= STOPS[0]) {
			return COLORS[0];
		}

		for (int index = 1; index < STOPS.length; index++) {
			if (position <= STOPS[index]) {
				float progress = (position - STOPS[index - 1]) / (STOPS[index] - STOPS[index - 1]);
				return interpolate(COLORS[index - 1], COLORS[index], progress);
			}
		}
		return COLORS[COLORS.length - 1];
	}

	private static int interpolate(int from, int to, float progress) {
		int red = mix((from >> 16) & 0xFF, (to >> 16) & 0xFF, progress);
		int green = mix((from >> 8) & 0xFF, (to >> 8) & 0xFF, progress);
		int blue = mix(from & 0xFF, to & 0xFF, progress);
		return (red << 16) | (green << 8) | blue;
	}

	private static int mix(int from, int to, float progress) {
		return Math.round(from + (to - from) * progress);
	}
}
