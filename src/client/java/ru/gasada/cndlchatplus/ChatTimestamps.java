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
import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class ChatTimestamps {
	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final Pattern PREFIX_PATTERN = Pattern.compile("^\\[\\d{2}:\\d{2}] ");
	private static final int MAX_TRACKED_PREFIXES = ResponderConfig.MAX_CHAT_HISTORY_LIMIT * 2;

	private final BooleanSupplier enabledSupplier;
	private final Supplier<ChatFormatting> colorSupplier;
	private final Set<Component> skipNext = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<IdentityKey, Boolean> ownPrefixes = new LinkedHashMap<>();

	public ChatTimestamps(BooleanSupplier enabledSupplier) {
		this(enabledSupplier, () -> ChatFormatting.GRAY);
	}

	ChatTimestamps(BooleanSupplier enabledSupplier, Supplier<ChatFormatting> colorSupplier) {
		this.enabledSupplier = enabledSupplier;
		this.colorSupplier = colorSupplier;
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
		ChatFormatting color = colorSupplier.get();
		if (color == null || TextColor.fromLegacyFormat(color) == null) color = ChatFormatting.GRAY;
		Component prefixed = Component.empty()
				.append(Component.literal("[" + time + "] ").withStyle(color)).append(message);
		ownPrefixes.put(new IdentityKey(prefixed), Boolean.TRUE);
		if (ownPrefixes.size() > MAX_TRACKED_PREFIXES) {
			ownPrefixes.remove(ownPrefixes.keySet().iterator().next());
		}
		return prefixed;
	}

	Component withoutOwnPrefix(Component message) {
		if (ownPrefixes.containsKey(new IdentityKey(message))
				&& message.getSiblings().size() == 2
				&& message.getSiblings().getFirst().getContents() instanceof PlainTextContents.LiteralContents literal
				&& PREFIX_PATTERN.matcher(literal.text()).matches()) {
			return message.getSiblings().get(1);
		}
		return message;
	}

	static String colorName(ChatFormatting color) {
		return switch (color) {
			case BLACK -> "Чёрный";
			case DARK_BLUE -> "Тёмно-синий";
			case DARK_GREEN -> "Тёмно-зелёный";
			case DARK_AQUA -> "Бирюзовый";
			case DARK_RED -> "Тёмно-красный";
			case DARK_PURPLE -> "Фиолетовый";
			case GOLD -> "Золотой";
			case GRAY -> "Серый";
			case DARK_GRAY -> "Тёмно-серый";
			case BLUE -> "Синий";
			case GREEN -> "Зелёный";
			case AQUA -> "Голубой";
			case RED -> "Красный";
			case LIGHT_PURPLE -> "Розовый";
			case YELLOW -> "Жёлтый";
			case WHITE -> "Белый";
			default -> "Серый";
		};
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
