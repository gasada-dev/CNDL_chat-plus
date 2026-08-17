package ru.gasada.cndlchatplus;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class ChatSearchState {
	private final BooleanSupplier enabledSupplier;
	private String query = "";
	private boolean active;

	public ChatSearchState(BooleanSupplier enabledSupplier) {
		this.enabledSupplier = enabledSupplier;
	}

	public boolean enabled() {
		return enabledSupplier.getAsBoolean();
	}

	public boolean active() {
		return enabled() && active;
	}

	public void activate() {
		active = enabled();
	}

	public void setQuery(String value) {
		query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	public boolean matches(String text) {
		return !active() || query.isEmpty()
				|| text != null && text.toLowerCase(Locale.ROOT).contains(query);
	}

	public void clear() {
		query = "";
		active = false;
	}
}
