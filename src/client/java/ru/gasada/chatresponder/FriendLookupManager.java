package ru.gasada.chatresponder;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class FriendLookupManager {
	private static final long COMMAND_DELAY_MS = 2_500;
	private static final long RESPONSE_TIMEOUT_MS = 7_000;
	private final ResponderConfig config;
	private final ServerCommandService commandService;
	private final ServerTemplateRuntime templateRuntime;
	private final Deque<String> queue = new ArrayDeque<>();
	private String pendingFriend;
	private String pendingLastSeen;
	private long pendingSince;
	private long nextCommandAt;

	public FriendLookupManager(ResponderConfig config) {
		this(config, null, ServerTemplateRuntime.fromLegacyConfig(config));
	}

	public FriendLookupManager(ResponderConfig config, ServerCommandService commandService) {
		this(config, commandService, ServerTemplateRuntime.fromLegacyConfig(config));
	}

	public FriendLookupManager(ResponderConfig config, ServerCommandService commandService,
			ServerTemplateRuntime templateRuntime) {
		this.config = config;
		this.commandService = commandService;
		this.templateRuntime = templateRuntime;
	}

	public void queueFriends(Collection<String> friends) {
		for (String friend : friends) {
			if (friend == null || friend.isBlank() || isAlreadyQueued(friend)) {
				continue;
			}
			queue.addLast(friend);
		}
	}

	public void resetRuntimeState() {
		queue.clear();
		pendingFriend = null;
		pendingLastSeen = null;
		pendingSince = 0L;
		nextCommandAt = 0L;
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.getConnection() == null) {
			queue.clear();
			pendingFriend = null;
			pendingLastSeen = null;
			return;
		}

		long now = System.currentTimeMillis();
		if (pendingFriend != null && now - pendingSince < RESPONSE_TIMEOUT_MS) {
			return;
		}
		if (pendingFriend != null) {
			finishLookup();
		}
		if (queue.isEmpty() || now < nextCommandAt) {
			return;
		}

		pendingFriend = queue.removeFirst();
		pendingLastSeen = null;
		pendingSince = now;
		if (commandService == null || !commandService.lookupFriend(pendingFriend).success()) {
			pendingFriend = null;
			pendingLastSeen = null;
			nextCommandAt = now + COMMAND_DELAY_MS;
		}
	}

	public boolean shouldShowSystemMessage(Component message, boolean overlay) {
		if (overlay) {
			return true;
		}

		String text = message.getString();
		LookupParseResult parsed = parseMessage(activeParser(), text);
		switch (parsed.type()) {
			case EMPTY_OR_TIMESTAMP, LOOKUP_OUTPUT -> {
				return false;
			}
			case LAST_SEEN -> {
				if (pendingFriend != null) {
					pendingLastSeen = parsed.value();
				}
				return false;
			}
			case INACTIVE -> {
				if (pendingFriend != null && pendingLastSeen == null) {
					String value = parsed.value();
					pendingLastSeen = value.toLowerCase(Locale.ROOT).endsWith("назад")
							? value : value + " назад";
				}
				return false;
			}
			case LOOKUP_END -> {
				if (pendingFriend != null) {
					finishLookup();
				}
				return false;
			}
			case UNRELATED -> {
				// The pending-player visibility check below is part of the existing manager state logic.
			}
		}
		if (pendingFriend == null) {
			return true;
		}

		String normalized = text.toLowerCase(Locale.ROOT);
		return !normalized.contains(pendingFriend.toLowerCase(Locale.ROOT));
	}

	static LookupParseResult parseMessage(String text) {
		return parseMessage(new FriendLookupParser(
				CompiledParserSettings.compile(ParserSettings.vanillaBoxDefaults())), text);
	}

	private static LookupParseResult parseMessage(FriendLookupParser parser, String text) {
		FriendLookupParser.ParseResult result = parser.parse(text);
		return new LookupParseResult(LookupMessageType.valueOf(result.type().name()), result.value());
	}

	private FriendLookupParser activeParser() {
		return new FriendLookupParser(templateRuntime.compiledParsers().orElseGet(() ->
				CompiledParserSettings.compile(new ParserSettings())));
	}

	private void finishLookup() {
		if (pendingFriend != null && pendingLastSeen != null) {
			String configName = config.friends.stream()
					.filter(friend -> friend.equalsIgnoreCase(pendingFriend))
					.findFirst()
					.orElse(pendingFriend);
			config.friendLastSeen.put(configName, pendingLastSeen);
			ConfigManager.save(config);
		}
		pendingFriend = null;
		pendingLastSeen = null;
		nextCommandAt = System.currentTimeMillis() + COMMAND_DELAY_MS;
	}

	private boolean isAlreadyQueued(String friend) {
		String normalized = friend.toLowerCase(Locale.ROOT);
		return pendingFriend != null && pendingFriend.equalsIgnoreCase(friend)
				|| queue.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized));
	}

	enum LookupMessageType {
		EMPTY_OR_TIMESTAMP,
		LAST_SEEN,
		INACTIVE,
		LOOKUP_END,
		LOOKUP_OUTPUT,
		UNRELATED
	}

	record LookupParseResult(LookupMessageType type, String value) {
	}
}
