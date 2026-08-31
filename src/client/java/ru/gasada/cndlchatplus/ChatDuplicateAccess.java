package ru.gasada.cndlchatplus;

import net.minecraft.network.chat.Component;

public interface ChatDuplicateAccess {
	boolean gasada$replaceLatest(Component expected, Component replacement);
}
