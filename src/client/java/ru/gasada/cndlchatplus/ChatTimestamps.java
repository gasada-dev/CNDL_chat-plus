package ru.gasada.cndlchatplus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class ChatTimestamps {
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final Pattern PREFIX_PATTERN = Pattern.compile("^\\[\\d{2}:\\d{2}] ");

	private final BooleanSupplier enabledSupplier;
	private final Set<Component> skipNext = Collections.newSetFromMap(new IdentityHashMap<>());

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
		// ponytail: skip-set чистится при disconnect через resetRuntimeState
		skipNext.add(prefixed);
		return prefixed;
	}

	public Component at(Component message, long timestamp) {
		return enabled() && !PREFIX_PATTERN.matcher(message.getString()).find()
				? withPrefix(message, timestamp) : message;
	}

	public Component counted(Component displayedBase, int count, long timestamp) {
		Component base = withoutOwnPrefix(displayedBase).copy().append(
				Component.literal(" x" + count).withStyle(ChatFormatting.GRAY));
		return enabled() ? withPrefix(base, timestamp) : base;
	}

	public void resetRuntimeState() {
		skipNext.clear();
	}

	private static Component withPrefix(Component message, long timestamp) {
		String time = FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
		return Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY).append(message);
	}

	private static Component withoutOwnPrefix(Component message) {
		if (message.getContents() instanceof PlainTextContents.LiteralContents literal
				&& PREFIX_PATTERN.matcher(literal.text()).matches()
				&& message.getSiblings().size() == 1) {
			return message.getSiblings().getFirst();
		}
		return message;
	}
}
