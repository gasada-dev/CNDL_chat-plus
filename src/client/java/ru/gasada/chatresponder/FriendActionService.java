package ru.gasada.chatresponder;

import java.util.ArrayList;

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
			if (usesQueuePreservingSave(snapshot.id())) {
				ConfigManager.saveVanillaBoxLastSeen(legacyConfig);
			} else {
				ConfigManager.save(legacyConfig);
			}
		}
		return true;
	}

	static boolean usesQueuePreservingSave(String templateId) {
		return LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID.equals(templateId);
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
		ActiveTemplateSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (!validation.valid() || snapshot == null
				|| snapshot.friends().stream().anyMatch(friend -> friend.equalsIgnoreCase(player))) {
			return false;
		}
		ArrayList<String> previous = new ArrayList<>(legacyConfig.friends);
		legacyConfig.friends = new ArrayList<>(snapshot.friends());
		legacyConfig.friends.add(player);
		if (ConfigManager.save(legacyConfig)) {
			return true;
		}
		legacyConfig.friends = previous;
		return false;
	}
}
