package ru.gasada.cndlchatplus;

public final class FriendsTabController {
	private final ResponderConfig config;
	public FriendsTabController(ResponderConfig config) { this.config = config; }
	public void add(String name) { config.friends.add(name); save(); }
	public void remove(String name) {
		config.friends.removeIf(v -> v.equalsIgnoreCase(name));
		config.friendLastSeen.keySet().removeIf(v -> v.equalsIgnoreCase(name));
		config.teleportAutoAcceptFriends.removeIf(v -> v.equalsIgnoreCase(name));
		save();
	}
	public void setHudEnabled(boolean enabled) { config.friendHudEnabled = enabled; save(); }
	public void setTeleportAutoAcceptMode(TeleportAutoAcceptMode mode) {
		config.teleportAutoAcceptMode = mode;
		save();
	}
	public void setTeleportAutoAccept(String friend, boolean enabled) {
		config.teleportAutoAcceptFriends.removeIf(value -> value.equalsIgnoreCase(friend));
		if (enabled) config.teleportAutoAcceptFriends.add(friend);
		save();
	}
	public boolean save() { return ConfigManager.save(config); }
}
