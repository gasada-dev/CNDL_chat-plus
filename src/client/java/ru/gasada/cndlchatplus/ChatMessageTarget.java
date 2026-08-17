package ru.gasada.cndlchatplus;

import net.minecraft.network.chat.Component;

public record ChatMessageTarget(Component component, boolean fromGame) {
}
