package ru.gasada.cndlchatplus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

final class ChatAccess {
	private ChatAccess() { }
	static ChatComponent chat(Minecraft minecraft) { return minecraft.gui.hud.getChat(); }
	static void addMessage(ChatComponent chat, Component component) { chat.addClientSystemMessage(component); }
}
