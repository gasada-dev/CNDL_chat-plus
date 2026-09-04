package ru.gasada.cndlchatplus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class ChatTimestamps {
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final Pattern PREFIX_PATTERN = Pattern.compile("^\\[\\d{2}:\\d{2}] ");
	private static final int MAX_TRACKED_PREFIXES = ResponderConfig.MAX_CHAT_HISTORY_LIMIT * 2;

	private final BooleanSupplier enabledSupplier;
	private final Set<Component> skipNext = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<IdentityKey, Boolean> ownPrefixes = new LinkedHashMap<>();

	public ChatTimestamps(BooleanSupplier enabledSupplier) {
		this.enabledSupplier = enabledSupplier;
	}

	public boolean enabled() {
		return enabledSupplier.getAsBoolean();
	}

	public Component apply(Component message) {
		if (!enabled() || skipNext.remove(message)
				|| PREFIX_PATTERN.matcher(message.getString()).find()) {
			return message;
		}
		Component prefixed = at(message, System.currentTimeMillis());
		ChatTabController tabs = CndlChatPlusClient.CHAT_TABS;
		if (tabs != null) {
			tabs.remapComponent(message, prefixed);
		}
		return prefixed;
	}

	public Component restored(Component message, long timestamp) {
		Component prefixed = at(message, timestamp);
		if (prefixed == message) return message;
		// ponytail: skip-set очищается lifecycle reset после вставки history
		skipNext.add(prefixed);
		return prefixed;
	}

	public Component at(Component message, long timestamp) {
		return enabled() && !PREFIX_PATTERN.matcher(message.getString()).find()
				? withOwnPrefix(message, timestamp) : message;
	}

	public Component counted(Component displayedBase, int count, long timestamp) {
		Component base = withoutOwnPrefix(displayedBase).copy().append(
				Component.literal(" x" + count).withStyle(ChatFormatting.GRAY));
		ownPrefixes.remove(new IdentityKey(displayedBase));
		return enabled() ? withOwnPrefix(base, timestamp) : base;
	}

	public void resetRuntimeState() {
		skipNext.clear();
	}

	public void resetConnectionState() {
		resetRuntimeState();
		ownPrefixes.clear();
	}

	private Component withOwnPrefix(Component message, long timestamp) {
		String time = FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
		Component prefixed = Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY).append(message);
		ownPrefixes.put(new IdentityKey(prefixed), Boolean.TRUE);
		if (ownPrefixes.size() > MAX_TRACKED_PREFIXES) {
			ownPrefixes.remove(ownPrefixes.keySet().iterator().next());
		}
		return prefixed;
	}

	Component withoutOwnPrefix(Component message) {
		if (ownPrefixes.containsKey(new IdentityKey(message))
				&& message.getContents() instanceof PlainTextContents.LiteralContents literal
				&& PREFIX_PATTERN.matcher(literal.text()).matches()
				&& message.getSiblings().size() == 1) {
			return message.getSiblings().getFirst();
		}
		return message;
	}

	int trackedPrefixCount() {
		return ownPrefixes.size();
	}

	private static final class IdentityKey {
		private final Component component;

		private IdentityKey(Component component) {
			this.component = component;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof IdentityKey key && component == key.component;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(component);
		}
	}
}
