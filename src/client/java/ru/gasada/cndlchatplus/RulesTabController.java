package ru.gasada.cndlchatplus;

public final class RulesTabController {
	private final ResponderConfig config;
	public RulesTabController(ResponderConfig config) { this.config = config; }
	public void setEnabled(boolean enabled) { config.enabled = enabled; }
	public void addRule() { config.rules.add(new ReplyRule("", "", ChatChannel.AUTO)); }
	public void removeRule(int index) { config.rules.remove(index); }
	public boolean save() { return ConfigManager.save(config); }
}
