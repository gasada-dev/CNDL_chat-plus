package ru.gasada.chatresponder;

public final class ChannelsTabController {
	private final ResponderConfig config;
	public ChannelsTabController(ResponderConfig config) { this.config = config; }
	public boolean save() { return ConfigManager.save(config); }
}
