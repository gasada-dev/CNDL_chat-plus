package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public final class ChatDuplicateCollapser {
	private final BooleanSupplier enabled;
	private Component original;
	private List<VisiblePart> originalVisible;
	private Component currentRaw;
	private Component displayedBase;
	private Component displayed;
	private Source source;
	private int count;
	private boolean awaitingDisplay;

	public ChatDuplicateCollapser() {
		this(() -> true);
	}

	public ChatDuplicateCollapser(BooleanSupplier enabled) {
		this.enabled = enabled;
	}

	public Decision incoming(Component message, Source messageSource) {
		if (!enabled.getAsBoolean()) {
			reset();
			return Decision.unique();
		}
		if (displayed != null && source == messageSource
				&& originalVisible.equals(visibleParts(message))) {
			int nextCount = count == Integer.MAX_VALUE ? count : count + 1;
			Component counted = original.copy().append(
					Component.literal(" x" + nextCount).withStyle(ChatFormatting.GRAY));
			return new Decision(true, displayed, displayedBase, currentRaw, counted, nextCount);
		}
		begin(message, messageSource);
		return Decision.unique();
	}

	public void observeDisplayed(Component displayedMessage) {
		if (!enabled.getAsBoolean()) {
			reset();
			return;
		}
		if (awaitingDisplay && count == 1) {
			displayedBase = displayedMessage;
			displayed = displayedMessage;
			awaitingDisplay = false;
		} else if (displayed != displayedMessage) {
			reset();
		}
	}

	public void replacementSucceeded(Component rawReplacement, Component displayedReplacement,
			int replacementCount) {
		currentRaw = rawReplacement;
		displayed = displayedReplacement;
		count = replacementCount;
		awaitingDisplay = false;
	}

	public void replacementFailed(Component message, Source messageSource) {
		begin(message, messageSource);
	}

	public void reset() {
		original = null;
		originalVisible = null;
		currentRaw = null;
		displayedBase = null;
		displayed = null;
		source = null;
		count = 0;
		awaitingDisplay = false;
	}

	private void begin(Component message, Source messageSource) {
		original = message.copy();
		originalVisible = visibleParts(message);
		currentRaw = original;
		displayedBase = null;
		displayed = null;
		source = messageSource;
		count = 1;
		awaitingDisplay = true;
	}

	private static List<VisiblePart> visibleParts(Component message) {
		List<VisiblePart> parts = new ArrayList<>();
		message.visit((style, text) -> {
			parts.add(new VisiblePart(text, style.getColor(), style.isBold(), style.isItalic(),
					style.isUnderlined(), style.isStrikethrough(), style.isObfuscated()));
			return Optional.empty();
		}, Style.EMPTY);
		return parts;
	}

	private record VisiblePart(String text, Object color, boolean bold, boolean italic,
			boolean underlined, boolean strikethrough, boolean obfuscated) { }

	public enum Source {
		CHAT,
		GAME
	}

	public record Decision(boolean duplicate, Component expectedDisplayed, Component displayedBase,
			Component expectedRaw, Component replacementRaw, int count) {
		private static Decision unique() {
			return new Decision(false, null, null, null, null, 1);
		}
	}
}
