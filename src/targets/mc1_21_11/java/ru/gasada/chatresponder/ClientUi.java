package ru.gasada.chatresponder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ClientUi {
	private ClientUi() { }
	static Screen currentScreen(Minecraft minecraft) { return minecraft.screen; }
	static void setScreen(Minecraft minecraft, Screen screen) { minecraft.setScreen(screen); }
}
