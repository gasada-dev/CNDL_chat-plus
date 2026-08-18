package ru.gasada.cndlchatplus;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class FriendLookupManager {
	private static final long COMMAND_DELAY_MS = 2_500;
	private static final long RESPONSE_TIMEOUT_MS = 7_000;
	private final ServerTemplateRuntime templateRuntime;
	private final FriendActionService actions;
	private final LongSupplier clock;
	private final ServerLookupCoordinator coordinator;
	private final Deque<LookupRequest> queue = new ArrayDeque<>();
	private LookupRequest pendingRequest;
	private String pendingLastSeen;
	private final Map<String, String> pendingPlayerInfo = new LinkedHashMap<>();
	private long pendingSince;
	private long nextCommandAt;
	private boolean suppressTrailingWhitespace;

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
		this(templateRuntime, actions, clock, new ServerLookupCoordinator());
	}

	FriendLookupManager(ServerTemplateRuntime templateRuntime, FriendActionService actions, LongSupplier clock,
			ServerLookupCoordinator coordinator) {
		this.templateRuntime = templateRuntime;
		this.actions = actions;
		this.clock = clock;
		this.coordinator = coordinator;
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
			queue.addLast(new LookupRequest(friend, null));
		}
	}

	public boolean queueManualLookup(String player, Consumer<PlayerLookupData> completion) {
		if (!PlayerNameValidator.validate(player).valid() || completion == null || isAlreadyQueued(player)) {
			return false;
		}
		queue.addLast(new LookupRequest(player, completion));
		return true;
	}

	public void queueActiveFriends() {
		templateRuntime.activeSnapshot().ifPresent(template -> queueFriends(template.friends()));
	}

	public void resetRuntimeState() {
		completePendingRequests();
		queue.clear();
		pendingRequest = null;
		pendingLastSeen = null;
		pendingPlayerInfo.clear();
		pendingSince = 0L;
		nextCommandAt = 0L;
		suppressTrailingWhitespace = false;
		coordinator.release(this);
	}

	public void tick(Minecraft minecraft) {
		tick(minecraft.getConnection() != null);
	}

	void tick(boolean connected) {
		if (!connected) {
			completePendingRequests();
			queue.clear();
			pendingRequest = null;
			pendingLastSeen = null;
			pendingPlayerInfo.clear();
			suppressTrailingWhitespace = false;
			coordinator.release(this);
			return;
		}

		long now = clock.getAsLong();
		if (pendingRequest != null && now - pendingSince < RESPONSE_TIMEOUT_MS) {
			return;
		}
		if (pendingRequest != null) {
			finishLookup();
		}
		if (queue.isEmpty() || now < nextCommandAt || !coordinator.tryAcquire(this)) {
			return;
		}

		pendingRequest = queue.removeFirst();
		pendingLastSeen = null;
		pendingPlayerInfo.clear();
		suppressTrailingWhitespace = false;
		pendingSince = now;
		if (!actions.lookup(pendingRequest.player()).success()) {
			LookupRequest failed = pendingRequest;
			pendingRequest = null;
			pendingLastSeen = null;
			nextCommandAt = now + COMMAND_DELAY_MS;
			coordinator.release(this);
			if (failed.completion() != null) failed.completion().accept(new PlayerLookupData(null, Map.of()));
		}
	}

	public boolean shouldShowSystemMessage(Component message, boolean overlay) {
		if (overlay || pendingRequest == null && !suppressTrailingWhitespace) {
			return true;
		}

		String text = message.getString();
		FriendLookupParser parser = activeParser();
		LookupParseResult parsed = parseMessage(parser, text);
		if (pendingRequest == null) {
			boolean trailingWhitespace = suppressTrailingWhitespace
					&& parsed.type() == LookupMessageType.EMPTY_OR_TIMESTAMP;
			suppressTrailingWhitespace = trailingWhitespace;
			return !trailingWhitespace;
		}
		switch (parsed.type()) {
			case EMPTY_OR_TIMESTAMP, LOOKUP_OUTPUT -> {
				return false;
			}
			case LAST_SEEN -> {
				if (pendingRequest != null) {
					pendingLastSeen = parsed.value();
				}
				return false;
			}
			case INACTIVE -> {
				if (pendingRequest != null && pendingLastSeen == null) {
					String value = parsed.value();
					pendingLastSeen = value.toLowerCase(Locale.ROOT).endsWith("назад")
							? value : value + " назад";
				}
				return false;
			}
			case PLAYER_INFO_FIELD -> {
				if (pendingRequest != null && parsed.fieldName() != null && parsed.value() != null) {
					pendingPlayerInfo.put(parsed.fieldName(), parsed.value());
					if (parser.isLookupEnd(text)) {
						suppressTrailingWhitespace = true;
						finishLookup();
					}
				}
				return false;
			}
			case LOOKUP_END -> {
				if (pendingRequest != null) {
					suppressTrailingWhitespace = true;
					finishLookup();
				}
				return false;
			}
			case UNRELATED -> {
				// The pending-player visibility check below is part of the existing manager state logic.
			}
		}
		String normalized = text.toLowerCase(Locale.ROOT);
		return !normalized.contains(pendingRequest.player().toLowerCase(Locale.ROOT));
	}

	static LookupParseResult parseMessage(String text) {
		return parseMessage(new FriendLookupParser(
				CompiledParserSettings.compile(ParserSettings.vanillaBoxDefaults())), text);
	}

	private static LookupParseResult parseMessage(FriendLookupParser parser, String text) {
		FriendLookupParser.ParseResult result = parser.parse(text);
		return new LookupParseResult(LookupMessageType.valueOf(result.type().name()), result.value(), result.fieldName());
	}

	private FriendLookupParser activeParser() {
		return new FriendLookupParser(templateRuntime.compiledParsers().orElseGet(() ->
				CompiledParserSettings.compile(new ParserSettings())));
	}

	private void finishLookup() {
		LookupRequest finished = pendingRequest;
		String finishedLastSeen = pendingLastSeen;
		Map<String, String> finishedPlayerInfo = Map.copyOf(pendingPlayerInfo);
		if (finished != null && finished.completion() == null && pendingLastSeen != null) {
			actions.updateLastSeen(finished.player(), pendingLastSeen);
		}
		pendingRequest = null;
		pendingLastSeen = null;
		pendingPlayerInfo.clear();
		nextCommandAt = clock.getAsLong() + COMMAND_DELAY_MS;
		coordinator.release(this);
		if (finished != null && finished.completion() != null) {
			finished.completion().accept(new PlayerLookupData(finishedLastSeen, finishedPlayerInfo));
		}
	}

	private void completePendingRequests() {
		if (pendingRequest != null && pendingRequest.completion() != null) {
			pendingRequest.completion().accept(new PlayerLookupData(null, Map.of()));
		}
		for (LookupRequest request : queue) {
			if (request.completion() != null) request.completion().accept(new PlayerLookupData(null, Map.of()));
		}
	}

	private boolean isAlreadyQueued(String friend) {
		String normalized = friend.toLowerCase(Locale.ROOT);
		return pendingRequest != null && pendingRequest.player().equalsIgnoreCase(friend)
				|| queue.stream().anyMatch(value -> value.player().toLowerCase(Locale.ROOT).equals(normalized));
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
		PLAYER_INFO_FIELD,
		UNRELATED
	}

	record LookupParseResult(LookupMessageType type, String value, String fieldName) {
	}

	private record LookupRequest(String player, Consumer<PlayerLookupData> completion) { }
}
