package ru.gasada.chatresponder;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

final class PlatformKeyMapping {
	private PlatformKeyMapping() { }
	static KeyMapping register(KeyMapping mapping) { return KeyBindingHelper.registerKeyBinding(mapping); }
}
