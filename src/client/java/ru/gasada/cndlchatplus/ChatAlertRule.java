package ru.gasada.cndlchatplus;

import java.util.UUID;

public final class ChatAlertRule {
	public String id;
	public String name;
	public boolean enabled;
	public ChatAlertMatchType matchType;
	public String pattern;
	public ChatAlertChannel channel;
	public boolean hudEnabled;
	public boolean soundEnabled;
	public int cooldownSeconds;

	public ChatAlertRule() {
		this(UUID.randomUUID().toString(), "Название", true, ChatAlertMatchType.TEXT, "",
				ChatAlertChannel.ANY, true, true, 0);
	}

	public ChatAlertRule(String id, String name, boolean enabled, ChatAlertMatchType matchType,
			String pattern, ChatAlertChannel channel, boolean hudEnabled, boolean soundEnabled,
			int cooldownSeconds) {
		this.id = id;
		this.name = name;
		this.enabled = enabled;
		this.matchType = matchType;
		this.pattern = pattern;
		this.channel = channel;
		this.hudEnabled = hudEnabled;
		this.soundEnabled = soundEnabled;
		this.cooldownSeconds = cooldownSeconds;
	}

	ChatAlertRule copy() {
		return new ChatAlertRule(id, name, enabled, matchType, pattern, channel, hudEnabled,
				soundEnabled, cooldownSeconds);
	}
}
