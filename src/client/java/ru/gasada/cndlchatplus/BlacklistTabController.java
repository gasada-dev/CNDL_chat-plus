package ru.gasada.cndlchatplus;

public final class BlacklistTabController {
	private final ResponderConfig config;
	public BlacklistTabController(ResponderConfig config) { this.config = config; }
	public void removeDiscord(String name) { config.discordMutedPlayers.removeIf(v -> v.equalsIgnoreCase(name)); save(); }
	public void removeWord(String word) { config.mutedWords.removeIf(v -> v.equalsIgnoreCase(word)); save(); }
	public void addDiscord(String name) { config.discordMutedPlayers.add(name); save(); }
	public void addWord(String word) { config.mutedWords.add(word); save(); }
	public boolean save() { return ConfigManager.save(config); }
}
