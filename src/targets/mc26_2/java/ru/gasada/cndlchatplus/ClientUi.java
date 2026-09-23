package ru.gasada.cndlchatplus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ClientUi {
	private ClientUi() { }
	static Screen currentScreen(Minecraft minecraft) { return minecraft.gui.screen(); }
	static void setScreen(Minecraft minecraft, Screen screen) { minecraft.gui.setScreen(screen); }
	static boolean themeScreenAvailable() { return true; }
	static void openThemeScreen(Minecraft minecraft, Screen parent) { setScreen(minecraft, new ThemeScreen(parent)); }
}
