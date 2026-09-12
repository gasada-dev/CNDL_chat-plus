package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.file.Path;

public final class FriendActionService {
	private final VanillaBoxRuntime runtime;
	private final ServerCommandService commands;
	private final ResponderConfig legacyConfig;
	private final Path configPath;

	public FriendActionService(VanillaBoxRuntime runtime, ServerCommandService commands,
			ResponderConfig legacyConfig) {
		this(runtime, commands, legacyConfig, null);
	}

	FriendActionService(VanillaBoxRuntime runtime, ServerCommandService commands,
			ResponderConfig legacyConfig, Path configPath) {
		this.runtime = runtime;
		this.commands = commands;
		this.legacyConfig = legacyConfig;
		this.configPath = configPath;
	}

	public boolean updateLastSeen(String player, String value) {
		VanillaBoxSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (snapshot == null) {
			return false;
		}
		String storedName = snapshot.friends().stream()
				.filter(friend -> friend.equalsIgnoreCase(player))
				.findFirst().orElse(null);
		if (storedName == null) {
			return false;
		}
		if (legacyConfig == null) return runtime.updateLastSeen(storedName, value).isPresent();
		LinkedHashMap<String, String> previous = new LinkedHashMap<>(legacyConfig.friendLastSeen);
		legacyConfig.friendLastSeen.keySet().removeIf(key -> key.equalsIgnoreCase(storedName));
		legacyConfig.friendLastSeen.put(storedName, value);
		boolean saved = configPath == null
				? ConfigManager.saveLastSeen(legacyConfig, runtime, storedName, value)
				: ConfigManager.saveLastSeen(legacyConfig, configPath, runtime, storedName, value);
		if (saved) return true;
		legacyConfig.friendLastSeen = previous;
		return false;
	}

	public ServerCommandService.CommandResult lookup(String player) {
		return commands == null
				? new ServerCommandService.CommandResult(false, "Сервис команд недоступен")
				: commands.lookupFriend(player);
	}

	public ServerCommandService.CommandResult privateMessage(String player, String message) {
		return commands.privateMessage(player, message);
	}

	public ServerCommandService.CommandResult pay(String player, String amount) {
		return commands.pay(player, amount);
	}

	public ServerCommandService.CommandResult call(String player) {
		return commands.call(player);
	}

	public ServerCommandService.CommandResult mail(String player, String message) {
		return commands.mail(player, message);
	}

	public boolean addFriend(String player) {
		PlayerNameValidator.ValidationResult validation = PlayerNameValidator.validate(player);
		VanillaBoxSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (!validation.valid() || snapshot == null
				|| snapshot.friends().stream().anyMatch(friend -> friend.equalsIgnoreCase(player))) {
			return false;
		}
		ArrayList<String> previous = new ArrayList<>(legacyConfig.friends);
		legacyConfig.friends = new ArrayList<>(snapshot.friends());
		legacyConfig.friends.add(player);
		boolean saved = configPath == null ? ConfigManager.save(legacyConfig)
				: ConfigManager.save(legacyConfig, configPath, runtime);
		if (saved) {
			return true;
		}
		legacyConfig.friends = previous;
		return false;
	}
}
