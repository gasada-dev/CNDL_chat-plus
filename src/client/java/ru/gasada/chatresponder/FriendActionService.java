package ru.gasada.chatresponder;

public final class FriendActionService {
	private final ServerTemplateRuntime runtime;
	private final ServerCommandService commands;
	private final ResponderConfig legacyConfig;

	public FriendActionService(ServerTemplateRuntime runtime, ServerCommandService commands,
			ResponderConfig legacyConfig) {
		this.runtime = runtime;
		this.commands = commands;
		this.legacyConfig = legacyConfig;
	}

	public boolean updateLastSeen(String player, String value) {
		ActiveTemplateSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (snapshot == null) {
			return false;
		}
		String storedName = snapshot.friends().stream()
				.filter(friend -> friend.equalsIgnoreCase(player))
				.findFirst().orElse(null);
		if (storedName == null) {
			return false;
		}
		runtime.updateActiveTemplate(template -> {
			template.friendLastSeen.keySet().removeIf(key -> key.equalsIgnoreCase(storedName));
			template.friendLastSeen.put(storedName, value);
		});
		if (legacyConfig != null) {
			legacyConfig.friendLastSeen.keySet().removeIf(key -> key.equalsIgnoreCase(storedName));
			legacyConfig.friendLastSeen.put(storedName, value);
			ConfigManager.save(legacyConfig);
		}
		return true;
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
}
