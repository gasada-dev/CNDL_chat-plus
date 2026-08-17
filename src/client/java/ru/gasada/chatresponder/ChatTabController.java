package ru.gasada.chatresponder;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ChatTabController {
	// ponytail: при переполнении карта чистится целиком; промах = fromGame false (LOCAL/SYSTEM
	// по маркерам всё равно определяются из текста)
	private static final int MAX_TRACKED = 16384;
	private static final int MAX_UNREAD = 999;

	private final ChatTabClassifier classifier;
	private final BooleanSupplier enabledSupplier;
	private final IdentityHashMap<Component, Boolean> gameMessages = new IdentityHashMap<>();
	private final Map<ChatTab, Integer> unread = new EnumMap<>(ChatTab.class);
	private ChatTab active = ChatTab.ALL;
	private boolean chatOpen;

	public ChatTabController(ChatTabClassifier classifier, BooleanSupplier enabledSupplier) {
		this.classifier = classifier;
		this.enabledSupplier = enabledSupplier;
	}

	public boolean enabled() {
		return enabledSupplier.getAsBoolean();
	}

	public ChatTab active() {
		return active;
	}

	public ChatTab classify(String displayed, boolean fromGame) {
		return classifier.classify(displayed, fromGame);
	}

	public void recordMessage(Component component, boolean fromGame) {
		mapSource(component, fromGame);
		ChatTab tab = classifier.classify(component.getString(), fromGame);
		if (!chatOpen || tab != active) {
			unread.merge(tab, 1, (current, one) -> Math.min(current + one, MAX_UNREAD));
		}
	}

	public void mapSource(Component component, boolean fromGame) {
		if (gameMessages.size() >= MAX_TRACKED) {
			gameMessages.clear();
		}
		gameMessages.put(component, fromGame);
	}

	public void remapComponent(Component original, Component prefixed) {
		Boolean fromGame = gameMessages.remove(original);
		if (fromGame != null) {
			gameMessages.put(prefixed, fromGame);
		}
	}

	public boolean isVisible(Component component, Boolean systemSource) {
		if (!enabled() || active == ChatTab.ALL) {
			return true;
		}
		boolean fromGame = systemSource != null
				? systemSource
				: Boolean.TRUE.equals(gameMessages.get(component));
		return classifier.classify(component.getString(), fromGame) == active;
	}

	public boolean fromGame(Component component, Boolean systemSource) {
		return systemSource != null ? systemSource : Boolean.TRUE.equals(gameMessages.get(component));
	}

	public int unread(ChatTab tab) {
		return unread.getOrDefault(tab, 0);
	}

	public void selectTab(ChatTab tab, Minecraft minecraft) {
		active = tab;
		unread.remove(tab);
		refresh(minecraft);
	}

	public void refresh(Minecraft minecraft) {
		if (minecraft != null) {
			((ChatTabFilterAccess) ChatAccess.chat(minecraft)).gasada$refreshTrimmed();
		}
	}

	public void chatOpened() {
		chatOpen = true;
		unread.remove(active);
	}

	public void chatClosed() {
		chatOpen = false;
	}

	public void resetRuntimeState() {
		gameMessages.clear();
		unread.clear();
	}

}
