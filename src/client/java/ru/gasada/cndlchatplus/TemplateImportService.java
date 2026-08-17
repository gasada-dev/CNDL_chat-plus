package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class TemplateImportService {
	private final ServerTemplateRepository repository;

	public TemplateImportService(ServerTemplateRepository repository) {
		this.repository = repository;
	}

	public TemplateOperationResult<TemplateImportPreview> preview(String sourceId, String targetId,
			TemplateImportOptions options) {
		if (sourceId == null || sourceId.equals(targetId)) {
			return TemplateOperationResult.failure("Source и target должны быть разными шаблонами", null);
		}
		TemplateOperationResult<ServerTemplate> source = repository.loadTemplate(sourceId);
		TemplateOperationResult<ServerTemplate> target = repository.loadTemplate(targetId);
		if (!source.success()) return TemplateOperationResult.failure(source.errorMessage(), source.error());
		if (!target.success()) return TemplateOperationResult.failure(target.errorMessage(), target.error());

		ServerTemplate draft = target.value().deepCopy(target.value().id, target.value().name);
		Map<TemplateImportOptions.Category, String> summary = new LinkedHashMap<>();
		applySelected(source.value(), draft, options, summary);
		List<String> errors = validateImportedSettings(draft, options);
		return TemplateOperationResult.success(new TemplateImportPreview(draft, summary, errors));
	}

	public TemplateOperationResult<ServerTemplate> apply(TemplateImportPreview preview, boolean confirmed) {
		if (preview == null || !confirmed) {
			return TemplateOperationResult.failure("Импорт не подтверждён", null);
		}
		if (!preview.valid()) {
			return TemplateOperationResult.failure(String.join("; ", preview.validationErrors()), null);
		}
		ServerTemplate result = preview.proposedTargetCopy();
		TemplateOperationResult<Void> saved = repository.saveTemplate(result);
		return saved.success() ? TemplateOperationResult.success(result)
				: TemplateOperationResult.failure(saved.errorMessage(), saved.error());
	}

	private static void applySelected(ServerTemplate source, ServerTemplate target,
			TemplateImportOptions options, Map<TemplateImportOptions.Category, String> summary) {
		for (TemplateImportOptions.Category category : TemplateImportOptions.Category.values()) {
			if (!options.selected(category)) continue;
			switch (category) {
				case REPLY_RULES -> target.rules = importList(target.rules, source.rules,
						options.listMode(category), TemplateImportService::ruleKey,
						ServerTemplate::copyRules);
				case CHANNELS_AND_MARKERS -> {
					target.globalPrefix = source.globalPrefix;
					target.clanReplyPrefix = source.clanReplyPrefix;
					target.privateReplyCommand = source.privateReplyCommand;
					target.globalMarkers = source.globalMarkers;
					target.clanMarkers = source.clanMarkers;
					target.privateMarkers = source.privateMarkers;
				}
				case MUTED_WORDS -> target.mutedWords = importStrings(target.mutedWords,
						source.mutedWords, options.listMode(category));
				case MUTED_MINECRAFT_PLAYERS -> target.mutedMinecraftPlayers = importStrings(
						target.mutedMinecraftPlayers, source.mutedMinecraftPlayers, options.listMode(category));
				case MUTED_DISCORD_USERS -> target.discordMutedPlayers = importStrings(
						target.discordMutedPlayers, source.discordMutedPlayers, options.listMode(category));
				case DISCORD_SETTINGS -> target.discordChatEnabled = source.discordChatEnabled;
				case FRIENDS -> target.friends = importStrings(target.friends, source.friends,
						options.listMode(category));
				case LAST_SEEN -> importLastSeen(source, target, options);
				case HUD_AND_SOUND -> {
					target.friendHudEnabled = source.friendHudEnabled;
					target.friendSoundEnabled = source.friendSoundEnabled;
				}
				case PERIODIC_MESSAGES -> target.periodicMessages = importPeriodic(target.periodicMessages,
						source.periodicMessages, options.listMode(category));
				case COMMANDS -> target.commands = source.commands.copy();
				case PARSER_PATTERNS -> target.parsers = source.parsers.copy();
				case PLAYER_INFO -> target.playerInfo = source.playerInfo.copy();
			}
			summary.put(category, describe(category, target));
		}
		if (!"vanilla-game".equals(target.id)) {
			target.commands.marriageList = "";
			target.parsers.marriageEntryPattern = "";
			target.parsers.marriagePagePattern = "";
			target.parsers.marriageEmptyPattern = "";
			target.playerInfo.marriageLookupConfigured = false;
		}
	}

	private static List<String> importStrings(List<String> target, List<String> source,
			TemplateImportOptions.ListMode mode) {
		if (mode == TemplateImportOptions.ListMode.SKIP) return new ArrayList<>(target);
		List<String> result = mode == TemplateImportOptions.ListMode.REPLACE
				? new ArrayList<>() : new ArrayList<>(target);
		for (String value : source) {
			if (result.stream().noneMatch(existing -> existing.equalsIgnoreCase(value))) result.add(value);
		}
		return result;
	}

	private static <T> List<T> importList(List<T> target, List<T> source,
			TemplateImportOptions.ListMode mode, Function<T, String> key,
			Function<List<T>, List<T>> copier) {
		if (mode == TemplateImportOptions.ListMode.SKIP) return copier.apply(target);
		List<T> result = mode == TemplateImportOptions.ListMode.REPLACE
				? new ArrayList<>() : copier.apply(target);
		for (T value : copier.apply(source)) {
			String sourceKey = key.apply(value);
			if (result.stream().noneMatch(existing -> key.apply(existing).equals(sourceKey))) result.add(value);
		}
		return result;
	}

	private static List<PeriodicMessageConfig> importPeriodic(List<PeriodicMessageConfig> target,
			List<PeriodicMessageConfig> source, TemplateImportOptions.ListMode mode) {
		List<PeriodicMessageConfig> result = new ArrayList<>();
		if (mode != TemplateImportOptions.ListMode.REPLACE) target.forEach(value -> result.add(value.copy()));
		if (mode != TemplateImportOptions.ListMode.SKIP) {
			for (PeriodicMessageConfig value : source) {
				String key = periodicKey(value);
				if (result.stream().noneMatch(existing -> periodicKey(existing).equals(key))) result.add(value.copy());
			}
		}
		return new ArrayList<>(result.subList(0,
				Math.min(result.size(), PeriodicMessageConfig.MAX_PERIODIC_MESSAGES)));
	}

	private static void importLastSeen(ServerTemplate source, ServerTemplate target,
			TemplateImportOptions options) {
		for (Map.Entry<String, String> entry : source.friendLastSeen.entrySet()) {
			String existingKey = target.friendLastSeen.keySet().stream()
					.filter(key -> key.equalsIgnoreCase(entry.getKey())).findFirst().orElse(null);
			if (existingKey == null) {
				target.friendLastSeen.put(entry.getKey(), entry.getValue());
			} else if (options.overwriteExistingLastSeen()) {
				target.friendLastSeen.put(existingKey, entry.getValue());
			}
		}
	}

	private static List<String> validateImportedSettings(ServerTemplate draft, TemplateImportOptions options) {
		List<String> errors = new ArrayList<>();
		if (options.selected(TemplateImportOptions.Category.COMMANDS)) {
			validateCommand(errors, draft.commands.ignorePlayer, CommandTemplateValidator.CommandType.IGNORE_PLAYER);
			validateCommand(errors, draft.commands.lookupFriend, CommandTemplateValidator.CommandType.LOOKUP_FRIEND);
			validateCommand(errors, draft.commands.privateMessage, CommandTemplateValidator.CommandType.PRIVATE_MESSAGE);
			validateCommand(errors, draft.commands.pay, CommandTemplateValidator.CommandType.PAY);
			validateCommand(errors, draft.commands.call, CommandTemplateValidator.CommandType.CALL);
			validateCommand(errors, draft.commands.mail, CommandTemplateValidator.CommandType.MAIL);
			if (draft.commands.marriageList != null && !draft.commands.marriageList.isBlank()) {
				validateCommand(errors, draft.commands.marriageList,
						CommandTemplateValidator.CommandType.MARRIAGE_LIST);
			}
		}
		if (options.selected(TemplateImportOptions.Category.PARSER_PATTERNS)) {
			ParserSettings p = draft.parsers;
			validatePattern(errors, p.discordMarkerPattern, false);
			validatePattern(errors, p.discordNamePattern, false);
			validatePattern(errors, p.lastSeenPattern, true);
			validatePattern(errors, p.inactivePattern, true);
			validatePattern(errors, p.lookupEndPattern, false);
			validatePattern(errors, p.lookupOutputPattern, false);
			validatePattern(errors, p.timestampOnlyPattern, false);
			validatePatternIfPresent(errors, p.marriageEntryPattern, 2);
			validatePatternIfPresent(errors, p.marriagePagePattern, 2);
			validatePatternIfPresent(errors, p.marriageEmptyPattern, 0);
		}
		return errors;
	}

	private static void validateCommand(List<String> errors, String value,
			CommandTemplateValidator.CommandType type) {
		CommandTemplateValidator.ValidationResult result = CommandTemplateValidator.validate(value, type);
		if (!result.valid()) errors.add(type + ": " + result.errorMessage());
	}

	private static void validatePattern(List<String> errors, String value, boolean capture) {
		ParserPatternValidator.ValidationResult result = ParserPatternValidator.validate(value, capture);
		if (!result.valid()) errors.add(result.errorMessage());
	}

	private static void validatePatternIfPresent(List<String> errors, String value, int captureGroups) {
		if (value == null || value.isBlank()) return;
		ParserPatternValidator.ValidationResult result = ParserPatternValidator.validate(value, captureGroups);
		if (!result.valid()) errors.add(result.errorMessage());
	}

	private static String ruleKey(ReplyRule value) {
		return (value.trigger + "\n" + value.response + "\n" + value.channel + "\n" + value.enabled)
				.toLowerCase(Locale.ROOT);
	}

	private static String periodicKey(PeriodicMessageConfig value) {
		return (value.message + "\n" + value.intervalMinutes + "\n" + value.enabled)
				.toLowerCase(Locale.ROOT);
	}

	private static String describe(TemplateImportOptions.Category category, ServerTemplate target) {
		return switch (category) {
			case REPLY_RULES -> target.rules.size() + " правил";
			case MUTED_WORDS -> target.mutedWords.size() + " фильтров";
			case MUTED_MINECRAFT_PLAYERS -> target.mutedMinecraftPlayers.size() + " Minecraft-мутов";
			case MUTED_DISCORD_USERS -> target.discordMutedPlayers.size() + " Discord-мутов";
			case FRIENDS -> target.friends.size() + " друзей";
			case LAST_SEEN -> target.friendLastSeen.size() + " last seen";
			case PERIODIC_MESSAGES -> target.periodicMessages.size() + " рассылок";
			default -> "заменено";
		};
	}
}
