package ru.gasada.chatresponder;

public final class PeriodicMessageConfig {
	public static final int MAX_PERIODIC_MESSAGES = 3;
	public boolean enabled;
	public String message = "";
	public int intervalMinutes = 5;

	public PeriodicMessageConfig() {
	}

	public PeriodicMessageConfig(boolean enabled, String message, int intervalMinutes) {
		this.enabled = enabled;
		this.message = message;
		this.intervalMinutes = intervalMinutes;
	}

	public PeriodicMessageConfig copy() {
		return new PeriodicMessageConfig(enabled, message, intervalMinutes);
	}
}
