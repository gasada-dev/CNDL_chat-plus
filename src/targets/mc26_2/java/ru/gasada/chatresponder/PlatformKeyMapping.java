package ru.gasada.chatresponder;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

final class PlatformKeyMapping {
	private PlatformKeyMapping() { }
	static KeyMapping register(KeyMapping mapping) { return KeyMappingHelper.registerKeyMapping(mapping); }
}
