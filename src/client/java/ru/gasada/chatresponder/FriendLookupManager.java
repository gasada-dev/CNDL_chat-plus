package ru.gasada.chatresponder;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Locale;
import java.util.function.LongSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class FriendLookupManager {
	private static final long COMMAND_DELAY_MS = 2_500;
	private static final long RESPONSE_TIMEOUT_MS = 7_000;
	private final ServerTemplateRuntime templateRuntime;
	private final FriendActionService actions;
	private final LongSupplier clock;
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
		this(templateRuntime, new FriendActionService(templateRuntime, commandService, config),
				System::currentTimeMillis);
	}

	FriendLookupManager(ServerTemplateRuntime templateRuntime, FriendActionService actions, LongSupplier clock) {
		this.templateRuntime = templateRuntime;
		this.actions = actions;
		this.clock = clock;
	}

	public void queueFriends(Collection<String> friends) {
		ActiveTemplateSnapshot template = templateRuntime.activeSnapshot().orElse(null);
		if (template == null) {
			return;
		}
		for (String friend : friends) {
			if (friend == null || friend.isBlank() || isAlreadyQueued(friend)
					|| template.friends().stream().noneMatch(value -> value.equalsIgnoreCase(friend))) {
				continue;
			}
			queue.addLast(friend);
		}
	}

	public void queueActiveFriends() {
		templateRuntime.activeSnapshot().ifPresent(template -> queueFriends(template.friends()));
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

		long now = clock.getAsLong();
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
		if (!actions.lookup(pendingFriend).success()) {
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
			actions.updateLastSeen(pendingFriend, pendingLastSeen);
		}
		pendingFriend = null;
		pendingLastSeen = null;
		nextCommandAt = clock.getAsLong() + COMMAND_DELAY_MS;
	}

	private boolean isAlreadyQueued(String friend) {
		String normalized = friend.toLowerCase(Locale.ROOT);
		return pendingFriend != null && pendingFriend.equalsIgnoreCase(friend)
				|| queue.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized));
	}

	int queuedCount() {
		return queue.size();
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
