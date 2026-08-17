package ru.gasada.cndlchatplus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class MarriageLookupManager {
	private static final long PAGE_DELAY_MS = 500;
	private static final long RESPONSE_TIMEOUT_MS = 7_000;
	private static final int MAX_PAGES = 100;

	private final ServerTemplateRuntime runtime;
	private final IntPredicate pageSender;
	private final ServerLookupCoordinator coordinator;
	private final LongSupplier clock;
	private final Deque<Request> queue = new ArrayDeque<>();
	private Request pending;
	private int requestedPage;
	private int maxPage;
	private boolean pageMarkerSeen;
	private boolean commandPending;
	private long nextCommandAt;
	private long pendingSince;

	public MarriageLookupManager(ServerTemplateRuntime runtime, ServerCommandService commands,
			ServerLookupCoordinator coordinator) {
		this(runtime, page -> commands.marriageList(page).success(), coordinator, System::currentTimeMillis);
	}

	MarriageLookupManager(ServerTemplateRuntime runtime, IntPredicate pageSender,
			ServerLookupCoordinator coordinator, LongSupplier clock) {
		this.runtime = runtime;
		this.pageSender = pageSender;
		this.coordinator = coordinator;
		this.clock = clock;
	}

	public boolean queueLookup(String player, Consumer<Result> completion) {
		if (!PlayerNameValidator.validate(player).valid() || completion == null || !configured()) return false;
		if (pending != null && pending.player().equalsIgnoreCase(player)
				|| queue.stream().anyMatch(value -> value.player().equalsIgnoreCase(player))) return false;
		queue.addLast(new Request(player, completion));
		return true;
	}

	public void tick(Minecraft minecraft) {
		tick(minecraft != null && minecraft.getConnection() != null);
	}

	void tick(boolean connected) {
		if (!connected) {
			resetRuntimeState();
			return;
		}
		long now = clock.getAsLong();
		if (pending == null) {
			if (queue.isEmpty() || !coordinator.tryAcquire(this)) return;
			pending = queue.removeFirst();
			requestedPage = 1;
			maxPage = 0;
			commandPending = true;
			nextCommandAt = now;
		}
		if (commandPending && now >= nextCommandAt) {
			if (!pageSender.test(requestedPage)) {
				finish(Result.failure("Не удалось отправить команду списка браков"));
				return;
			}
			commandPending = false;
			pageMarkerSeen = false;
			pendingSince = now;
		}
		if (!commandPending && now - pendingSince >= RESPONSE_TIMEOUT_MS) {
			finish(Result.failure("Список браков не ответил вовремя"));
		}
	}

	public boolean shouldShowSystemMessage(Component message, boolean overlay) {
		return overlay || handleMessage(message.getString());
	}

	boolean handleMessage(String text) {
		if (pending == null || commandPending) return true;
		CompiledParserSettings parsers = runtime.compiledParsers().orElse(null);
		if (parsers == null) return true;

		if (parsers.marriageEmpty().map(pattern -> pattern.matcher(text).find()).orElse(false)) {
			finish(Result.notFound());
			return false;
		}
		Matcher entry = parsers.marriageEntry().map(pattern -> pattern.matcher(text)).orElse(null);
		if (entry != null && entry.find()) {
			String first = entry.group(1);
			String second = entry.group(2);
			if (pending.player().equalsIgnoreCase(first)) finish(Result.found(second));
			else if (pending.player().equalsIgnoreCase(second)) finish(Result.found(first));
			return false;
		}
		Matcher page = parsers.marriagePage().map(pattern -> pattern.matcher(text)).orElse(null);
		if (page != null && page.find()) {
			int current = parsePage(page.group(1));
			int total = parsePage(page.group(2));
			if (current == requestedPage && total >= current && total <= MAX_PAGES) {
				maxPage = total;
				if (pageMarkerSeen) completePage();
				else pageMarkerSeen = true;
			}
			return false;
		}
		return true;
	}

	public void resetRuntimeState() {
		if (pending != null) pending.completion().accept(Result.failure("Поиск брака отменён"));
		for (Request request : queue) request.completion().accept(Result.failure("Поиск брака отменён"));
		queue.clear();
		pending = null;
		commandPending = false;
		coordinator.release(this);
	}

	private boolean configured() {
		ActiveTemplateSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		CompiledParserSettings parsers = runtime.compiledParsers().orElse(null);
		return snapshot != null && "vanilla-game".equals(snapshot.id())
				&& snapshot.commands().marriageList() != null
				&& !snapshot.commands().marriageList().isBlank() && parsers != null
				&& parsers.marriageEntry().isPresent() && parsers.marriagePage().isPresent();
	}

	private void completePage() {
		if (requestedPage >= maxPage) {
			finish(Result.notFound());
			return;
		}
		requestedPage++;
		commandPending = true;
		nextCommandAt = clock.getAsLong() + PAGE_DELAY_MS;
	}

	private void finish(Result result) {
		Request finished = pending;
		pending = null;
		commandPending = false;
		coordinator.release(this);
		if (finished != null) finished.completion().accept(result);
	}

	private static int parsePage(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	public record Result(String partner, boolean completed, String errorMessage) {
		static Result found(String partner) { return new Result(partner, true, ""); }
		static Result notFound() { return new Result(null, true, ""); }
		static Result failure(String error) { return new Result(null, false, error); }
	}

	private record Request(String player, Consumer<Result> completion) { }
}
