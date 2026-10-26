package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class TeleportRequestButton {
	static final long TIMEOUT_MILLIS = 60_000L;
	private static final int HEIGHT = 20;
	private static final int MAX_VISIBLE = 3;
	private static final SoundEvent REQUEST_SOUND = SoundEvent.createVariableRangeEvent(
			Identifier.fromNamespaceAndPath(CndlChatPlusClient.MOD_ID, "teleport_request"));

	private final VanillaBoxRuntime runtime;
	private final ServerCommandService commands;
	private final LongSupplier clock;
	private final Runnable soundPlayer;
	private final BooleanSupplier soundEnabled;
	private final List<PendingRequest> pending = new ArrayList<>();
	private final Set<PendingRequest> accepting = new HashSet<>();
	private boolean playSound;

	public TeleportRequestButton(VanillaBoxRuntime runtime, ServerCommandService commands) {
		this(runtime, commands, System::currentTimeMillis, () -> Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(REQUEST_SOUND, 1.0F)),
				() -> Boolean.TRUE.equals(CndlChatPlusClient.CONFIG.teleportRequestSoundEnabled));
	}

	TeleportRequestButton(VanillaBoxRuntime runtime, ServerCommandService commands, LongSupplier clock) {
		this(runtime, commands, clock, () -> { }, () -> true);
	}

	TeleportRequestButton(VanillaBoxRuntime runtime, ServerCommandService commands, LongSupplier clock,
			Runnable soundPlayer) {
		this(runtime, commands, clock, soundPlayer, () -> true);
	}

	TeleportRequestButton(VanillaBoxRuntime runtime, ServerCommandService commands, LongSupplier clock,
			Runnable soundPlayer, BooleanSupplier soundEnabled) {
		this.runtime = runtime;
		this.commands = commands;
		this.clock = clock;
		this.soundPlayer = soundPlayer;
		this.soundEnabled = soundEnabled;
	}

	public void handleMessage(String message) {
		VanillaBoxSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (snapshot == null || !commands.supports(CommandTemplateValidator.CommandType.ACCEPT_TELEPORT)) {
			return;
		}
		Matcher matcher = snapshot.compiledParsers().teleportRequest()
				.map(pattern -> pattern.matcher(ChatMessageTextSanitizer.stripSyntheticLabels(message).trim()))
				.orElse(null);
		if (matcher == null || !matcher.find()) {
			return;
		}
		String requester = matcher.group(1);
		if (!PlayerNameValidator.validate(requester).valid()) {
			return;
		}
		prune();
		PendingRequest request = new PendingRequest(requester, snapshot.generation(), clock.getAsLong() + TIMEOUT_MILLIS);
		int existing = -1;
		for (int index = 0; index < pending.size(); index++) {
			if (pending.get(index).requester().equalsIgnoreCase(requester)) {
				existing = index;
				break;
			}
		}
		if (existing < 0) pending.add(request);
		else pending.set(existing, request);
		if (shouldAutoAccept(snapshot, requester) && accept(request)) return;
		playSound = true;
	}

	private static boolean shouldAutoAccept(VanillaBoxSnapshot snapshot, String requester) {
		return switch (snapshot.teleportAutoAcceptMode()) {
			case OFF -> false;
			case EVERYONE -> true;
			case FRIENDS -> containsIgnoringCase(snapshot.friends(), requester);
			case SELECTED_FRIENDS -> containsIgnoringCase(snapshot.teleportAutoAcceptFriends(), requester);
		};
	}

	private static boolean containsIgnoringCase(java.util.List<String> names, String requester) {
		return names.stream().anyMatch(name -> name.equalsIgnoreCase(requester));
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.getConnection() == null) {
			resetRuntimeState();
			return;
		}
		prune();
		playPendingSound();
	}

	public boolean click(double mouseX, double mouseY, int screenWidth, Font font) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return false;
		List<PendingRequest> visible = visibleRequests();
		for (int row = 0; row < visible.size(); row++) {
			PendingRequest request = visible.get(row);
			if (bounds(screenWidth, font, request.requester(), row).contains(mouseX, mouseY)) {
				accept(request.requester());
				return true;
			}
		}
		return false;
	}

	boolean accept(String requester) {
		prune();
		PendingRequest request = pending.stream().filter(value -> value.requester().equalsIgnoreCase(requester))
				.findFirst().orElse(null);
		return request != null && accept(request);
	}

	private boolean accept(PendingRequest request) {
		if (!accepting.add(request)) return true;
		return commands.acceptTeleport(request.requester(), result -> {
			accepting.remove(request);
			if (!pending.contains(request)) return;
			if (result.state() == OutgoingChatService.SendState.SENT) pending.remove(request);
			else playSound = true;
		}).success();
	}

	public void resetRuntimeState() {
		pending.clear();
		accepting.clear();
		playSound = false;
	}

	public void register() {
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(CndlChatPlusClient.MOD_ID, "teleport_request"),
				(graphics, deltaTracker) -> render(new CompatGraphics(graphics)));
	}

	boolean visible() {
		return !visibleRequests().isEmpty();
	}

	String requester() {
		List<PendingRequest> visible = visibleRequests();
		return visible.isEmpty() ? null : visible.getFirst().requester();
	}

	List<String> visibleRequesters() {
		return visibleRequests().stream().map(PendingRequest::requester).toList();
	}

	void playPendingSound() {
		if (!playSound) {
			return;
		}
		playSound = false;
		if (soundEnabled.getAsBoolean()) soundPlayer.run();
	}

	private void render(CompatGraphics graphics) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return;
		Minecraft minecraft = Minecraft.getInstance();
		List<PendingRequest> visible = visibleRequests();
		for (int row = 0; row < visible.size(); row++) {
			PendingRequest request = visible.get(row);
			Bounds bounds = bounds(graphics.guiWidth(), minecraft.font, request.requester(), row);
			graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + HEIGHT,
					ThemeTokens.surfaceSecondary());
			graphics.outline(bounds.x(), bounds.y(), bounds.width(), HEIGHT, ThemeTokens.success());
			graphics.centeredText(minecraft.font, label(request), bounds.x() + bounds.width() / 2,
					bounds.y() + 6, ThemeTokens.text());
		}
	}

	private void prune() {
		long generation = runtime.activeSnapshot().map(VanillaBoxSnapshot::generation).orElse(-1L);
		long now = clock.getAsLong();
		pending.removeIf(request -> request.expiresAt() <= now || request.generation() != generation);
		accepting.retainAll(pending);
	}

	private List<PendingRequest> visibleRequests() {
		prune();
		return pending.stream().limit(MAX_VISIBLE).toList();
	}

	private String label(PendingRequest request) {
		long seconds = Math.max(1L, (request.expiresAt() - clock.getAsLong() + 999L) / 1_000L);
		return "Принять ТП от " + request.requester() + " (" + seconds + ")";
	}

	private static Bounds bounds(int screenWidth, Font font, String requester, int row) {
		int width = Math.min(screenWidth - 4, font.width("Принять ТП от " + requester + " (60)") + 16);
		return new Bounds((screenWidth - width) / 2, 8 + row * (HEIGHT + 4), width);
	}

	private record PendingRequest(String requester, long generation, long expiresAt) { }

	private record Bounds(int x, int y, int width) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT;
		}
	}
}
