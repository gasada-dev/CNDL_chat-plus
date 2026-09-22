package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

final class DiscordMarkerDisplayTest {
	@Test
	void replacesDiscordMarkerWithoutMutatingOriginalMessage() {
		Component original = Component.literal("(Discord) 142200 » Ты тут?");
		Component displayed = DiscordMarkerDisplay.compact(original);

		assertEquals("(Discord) 142200 » Ты тут?", original.getString());
		assertEquals("(D) 142200 » Ты тут?", displayed.getString());
	}
}
