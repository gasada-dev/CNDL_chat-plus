package ru.gasada.cndlchatplus;

import java.util.Map;
import java.util.Optional;

public final class ServerCommandService {
	private final ServerTemplateRuntime runtime;
	private final OutgoingChatService outgoing;

	public ServerCommandService(ServerTemplateRuntime runtime, OutgoingChatService outgoing) {
		this.runtime = runtime;
		this.outgoing = outgoing;
	}

	public CommandResult ignorePlayer(String player) {
		return playerCommand(CommandTemplateValidator.CommandType.IGNORE_PLAYER, command("ignorePlayer"), player);
	}

	public CommandResult lookupFriend(String player) {
		return playerCommand(CommandTemplateValidator.CommandType.LOOKUP_FRIEND, command("lookupFriend"), player);
	}

	public CommandResult privateMessage(String player, String message) {
		return messageCommand(CommandTemplateValidator.CommandType.PRIVATE_MESSAGE,
				command("privateMessage"), player, message, MessageValidator.MessageType.PRIVATE_MESSAGE);
	}

	public CommandResult pay(String player, String amount) {
		PlayerNameValidator.ValidationResult playerResult = PlayerNameValidator.validate(player);
		if (!playerResult.valid()) {
			return CommandResult.failure(playerResult.errorMessage());
		}
		AmountValidator.AmountValidationResult amountResult = AmountValidator.validate(amount);
		if (!amountResult.valid()) {
			return CommandResult.failure(amountResult.errorMessage());
		}
		return expandAndSend(CommandTemplateValidator.CommandType.PAY, command("pay"),
				Map.of("player", player, "amount", amountResult.normalizedAmount()));
	}

	public CommandResult call(String player) {
		return playerCommand(CommandTemplateValidator.CommandType.CALL, command("call"), player);
	}

	public CommandResult mail(String player, String message) {
		return messageCommand(CommandTemplateValidator.CommandType.MAIL,
				command("mail"), player, message, MessageValidator.MessageType.MAIL);
	}

	public CommandResult marriageList(int page) {
		if (page < 1 || page > 1_000) {
			return CommandResult.failure("Некорректная страница списка браков");
		}
		return expandAndSend(CommandTemplateValidator.CommandType.MARRIAGE_LIST, command("marriageList"),
				Map.of("page", Integer.toString(page)));
	}

	public Optional<String> privateMessageDraft(String player) {
		return playerDraft(CommandTemplateValidator.CommandType.PRIVATE_MESSAGE,
				command("privateMessage"), player, "message");
	}

	public Optional<String> payDraft(String player) {
		return playerDraft(CommandTemplateValidator.CommandType.PAY, command("pay"), player, "amount");
	}

	public Optional<String> mailDraft(String player) {
		return playerDraft(CommandTemplateValidator.CommandType.MAIL, command("mail"), player, "message");
	}

	public boolean supports(CommandTemplateValidator.CommandType type) {
		Optional<String> template = switch (type) {
			case IGNORE_PLAYER -> command("ignorePlayer");
			case LOOKUP_FRIEND -> command("lookupFriend");
			case PRIVATE_MESSAGE -> command("privateMessage");
			case PAY -> command("pay");
			case CALL -> command("call");
			case MAIL -> command("mail");
			case MARRIAGE_LIST -> command("marriageList");
		};
		return template.filter(value -> CommandTemplateValidator.validate(value, type).valid()).isPresent();
	}

	public boolean supportsDraft(CommandTemplateValidator.CommandType type) {
		String placeholder = switch (type) {
			case PRIVATE_MESSAGE, MAIL -> "message";
			case PAY -> "amount";
			default -> null;
		};
		if (placeholder == null) return false;
		Optional<String> template = switch (type) {
			case PRIVATE_MESSAGE -> command("privateMessage");
			case PAY -> command("pay");
			case MAIL -> command("mail");
			default -> Optional.empty();
		};
		return template.filter(value -> {
			CommandTemplateValidator.ValidationResult validation = CommandTemplateValidator.validate(value, type);
			return validation.valid() && validation.normalizedTemplate().endsWith("{" + placeholder + "}");
		}).isPresent();
	}

	private Optional<String> playerDraft(CommandTemplateValidator.CommandType type, Optional<String> template,
			String player, String remainingPlaceholder) {
		PlayerNameValidator.ValidationResult playerResult = PlayerNameValidator.validate(player);
		if (!playerResult.valid() || template.isEmpty()) {
			return Optional.empty();
		}
		CommandTemplateValidator.ValidationResult validation = CommandTemplateValidator.validate(template.get(), type);
		if (!validation.valid()) {
			return Optional.empty();
		}
		if (!validation.normalizedTemplate().endsWith("{" + remainingPlaceholder + "}")) {
			return Optional.empty();
		}
		String command = validation.normalizedTemplate().replace("{player}", player)
				.replace("{" + remainingPlaceholder + "}", "");
		return Optional.of("/" + command);
	}

	private CommandResult playerCommand(CommandTemplateValidator.CommandType type,
			Optional<String> template, String player) {
		PlayerNameValidator.ValidationResult playerResult = PlayerNameValidator.validate(player);
		if (!playerResult.valid()) {
			return CommandResult.failure(playerResult.errorMessage());
		}
		return expandAndSend(type, template, Map.of("player", player));
	}

	private CommandResult messageCommand(CommandTemplateValidator.CommandType type, Optional<String> template,
			String player, String message, MessageValidator.MessageType messageType) {
		PlayerNameValidator.ValidationResult playerResult = PlayerNameValidator.validate(player);
		if (!playerResult.valid()) {
			return CommandResult.failure(playerResult.errorMessage());
		}
		MessageValidator.ValidationResult messageResult = MessageValidator.validate(message, messageType);
		if (!messageResult.valid()) {
			return CommandResult.failure(messageResult.errorMessage());
		}
		return expandAndSend(type, template, Map.of("player", player, "message", message.trim()));
	}

	private CommandResult expandAndSend(CommandTemplateValidator.CommandType type, Optional<String> template,
			Map<String, String> values) {
		if (template.isEmpty() || template.get().isBlank()) {
			return CommandResult.failure("Команда отсутствует в активном шаблоне");
		}
		CommandTemplateValidator.ValidationResult validation = CommandTemplateValidator.validate(template.get(), type);
		if (!validation.valid()) {
			return CommandResult.failure(validation.errorMessage());
		}
		String command = validation.normalizedTemplate();
		for (var entry : values.entrySet()) {
			command = command.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		OutgoingChatService.SendResult sent = outgoing.sendCommand(command);
		return sent.success() ? CommandResult.ok() : CommandResult.failure(sent.errorMessage());
	}

	private Optional<String> command(String name) {
		return runtime.activeSnapshot().map(ActiveTemplateSnapshot::commands).map(commands -> switch (name) {
			case "ignorePlayer" -> commands.ignorePlayer();
			case "lookupFriend" -> commands.lookupFriend();
			case "privateMessage" -> commands.privateMessage();
			case "pay" -> commands.pay();
			case "call" -> commands.call();
			case "mail" -> commands.mail();
			case "marriageList" -> commands.marriageList();
			default -> "";
		});
	}

	public record CommandResult(boolean success, String errorMessage) {
		private static CommandResult ok() {
			return new CommandResult(true, "");
		}

		private static CommandResult failure(String errorMessage) {
			return new CommandResult(false, errorMessage);
		}
	}
}
