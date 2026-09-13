package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final ChatTimestamps timestamps;
	private final IdentityHashMap<Component, Boolean> gameMessages = new IdentityHashMap<>();
	private final IdentityHashMap<Component, Boolean> restoredMessages = new IdentityHashMap<>();
	private final Map<ChatTab, Integer> unread = new EnumMap<>(ChatTab.class);
	private final Map<String, Integer> customUnread = new HashMap<>();
	private List<ChatTabDefinition> customTabs = List.of();
	private List<ChatTabDefinition> definitions = List.of(ChatTabDefinition.builtIn(ChatTab.ALL));
	private ChatTab active = ChatTab.ALL;
	private String activeCustomId;
	private boolean chatOpen;

	public ChatTabController(ChatTabClassifier classifier, BooleanSupplier enabledSupplier) {
		this(classifier, enabledSupplier, ResponderConfig.defaults(), null);
	}

	public ChatTabController(ChatTabClassifier classifier, BooleanSupplier enabledSupplier,
			ResponderConfig config, ChatTimestamps timestamps) {
		this.classifier = classifier;
		this.enabledSupplier = enabledSupplier;
		this.timestamps = timestamps;
		reloadConfig(config);
	}

	public boolean enabled() {
		return enabledSupplier.getAsBoolean();
	}

	public ChatTab active() {
		return active;
	}

	public ChatTabDefinition activeDefinition() {
		if (activeCustomId != null) {
			for (ChatTabDefinition tab : customTabs) {
				if (tab.id().equals(activeCustomId)) return tab;
			}
		}
		for (ChatTabDefinition definition : definitions) {
			if (definition.builtIn() == active) return definition;
		}
		return ChatTabDefinition.builtIn(ChatTab.ALL);
	}

	public List<ChatTabDefinition> definitions() {
		return definitions;
	}

	public ChatTab classify(String displayed, boolean fromGame) {
		return classifier.classify(displayed, fromGame);
	}

	public void recordMessage(Component component, boolean fromGame) {
		mapSource(component, fromGame);
		ChatTab tab = classifier.classify(component.getString(), fromGame);
		if (!chatOpen || tab != active) {
			increment(unread, tab);
		}
		ChatTabSource source = source(component, tab);
		for (ChatTabDefinition custom : customTabs) {
			String id = custom.id();
			if (custom.sources().contains(source)
					&& (!chatOpen || !id.equals(activeCustomId))) {
				increment(customUnread, id);
			}
		}
	}

	public void mapSource(Component component, boolean fromGame) {
		if (gameMessages.size() >= MAX_TRACKED) {
			gameMessages.clear();
		}
		gameMessages.put(component, fromGame);
	}

	public void mapRestoredSource(Component component, boolean fromGame) {
		mapSource(component, fromGame);
		if (restoredMessages.size() >= MAX_TRACKED) restoredMessages.clear();
		restoredMessages.put(component, fromGame);
	}

	public void remapComponent(Component original, Component prefixed) {
		Boolean fromGame = gameMessages.remove(original);
		if (fromGame != null) {
			gameMessages.put(prefixed, fromGame);
		}
		Boolean restored = restoredMessages.remove(original);
		if (restored != null) restoredMessages.put(prefixed, restored);
	}

	public boolean isVisible(Component component, Boolean systemSource) {
		if (!enabled() || activeCustomId == null && active == ChatTab.ALL) {
			return true;
		}
		if (activeCustomId != null) {
			boolean fromGame = fromGame(component, systemSource);
			ChatTabSource source = source(component, classifier.classify(component.getString(), fromGame));
			for (ChatTabDefinition tab : customTabs) {
				if (tab.id().equals(activeCustomId)) {
					return tab.sources().contains(source);
				}
			}
			return true;
		}
		boolean fromGame = fromGame(component, systemSource);
		return classifier.classify(component.getString(), fromGame) == active;
	}

	public boolean fromGame(Component component, Boolean systemSource) {
		Boolean restored = restoredMessages.get(component);
		return restored != null ? restored
				: systemSource != null ? systemSource : Boolean.TRUE.equals(gameMessages.get(component));
	}

	public int unread(ChatTab tab) {
		return unread.getOrDefault(tab, 0);
	}

	public int unread(ChatTabDefinition tab) {
		return tab.custom() ? customUnread.getOrDefault(tab.id(), 0) : unread(tab.builtIn());
	}

	public void selectTab(ChatTab tab, Minecraft minecraft) {
		ChatTab selected = definitions.stream().anyMatch(definition -> definition.builtIn() == tab)
				? tab : ChatTab.ALL;
		active = selected;
		activeCustomId = null;
		unread.remove(selected);
		refresh(minecraft);
	}

	public void selectTab(ChatTabDefinition tab, Minecraft minecraft) {
		if (tab == null || !tab.custom()) {
			selectTab(tab == null ? ChatTab.ALL : tab.builtIn(), minecraft);
			return;
		}
		for (ChatTabDefinition custom : customTabs) {
			if (custom.id().equals(tab.id())) {
				active = ChatTab.ALL;
				activeCustomId = tab.id();
				customUnread.remove(tab.id());
				refresh(minecraft);
				return;
			}
		}
		active = ChatTab.ALL;
		activeCustomId = null;
		refresh(minecraft);
	}

	public void reloadConfig(ResponderConfig config) {
		ResponderConfig source = config == null ? ResponderConfig.defaults() : config;
		source.sanitize();
		Set<String> hidden = new HashSet<>(source.hiddenBuiltInTabs);
		ArrayList<ChatTabDefinition> nextDefinitions = new ArrayList<>();
		for (ChatTab tab : ChatTab.values()) {
			if (tab == ChatTab.ALL || !hidden.contains(tab.name())) {
				nextDefinitions.add(ChatTabDefinition.builtIn(tab));
			}
		}
		ArrayList<ChatTabDefinition> nextCustom = new ArrayList<>();
		for (CustomChatTab tab : source.customChatTabs) {
			ChatTabDefinition definition = ChatTabDefinition.custom(tab);
			nextDefinitions.add(definition);
			nextCustom.add(definition);
		}
		definitions = List.copyOf(nextDefinitions);
		customTabs = List.copyOf(nextCustom);
		customUnread.keySet().removeIf(id -> customTabs.stream()
				.noneMatch(tab -> tab.id().equals(id)));
		if (activeCustomId != null && customTabs.stream()
				.noneMatch(tab -> tab.id().equals(activeCustomId))
				|| hidden.contains(active.name())) {
			active = ChatTab.ALL;
			activeCustomId = null;
		}
	}

	public void refresh(Minecraft minecraft) {
		if (minecraft != null) {
			((ChatTabFilterAccess) ChatAccess.chat(minecraft)).gasada$refreshTrimmed();
		}
	}

	public void chatOpened() {
		chatOpen = true;
		if (activeCustomId == null) unread.remove(active);
		else customUnread.remove(activeCustomId);
	}

	public void chatClosed() {
		chatOpen = false;
	}

	public void resetRuntimeState() {
		gameMessages.clear();
		restoredMessages.clear();
		unread.clear();
		customUnread.clear();
		active = ChatTab.ALL;
		activeCustomId = null;
		chatOpen = false;
	}

	private ChatTabSource source(Component component, ChatTab classified) {
		String canonical = ChatMessageTextSanitizer.canonicalMessageText(component, timestamps);
		return ChatTabSource.classify(classified,
				ChatMessageTextSanitizer.stripDisplayFormatting(canonical));
	}

	private static <K> void increment(Map<K, Integer> counts, K key) {
		counts.merge(key, 1, (current, one) -> Math.min(current + one, MAX_UNREAD));
	}

}
