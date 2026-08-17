package ru.gasada.cndlchatplus;

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
	private static final SoundEvent REQUEST_SOUND = SoundEvent.createVariableRangeEvent(
			Identifier.fromNamespaceAndPath(CndlChatPlusClient.MOD_ID, "teleport_request"));

	private final ServerTemplateRuntime runtime;
	private final ServerCommandService commands;
	private final LongSupplier clock;
	private final Runnable soundPlayer;
	private PendingRequest pending;
	private boolean playSound;

	public TeleportRequestButton(ServerTemplateRuntime runtime, ServerCommandService commands) {
		this(runtime, commands, System::currentTimeMillis, () -> Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(REQUEST_SOUND, 1.0F)));
	}

	TeleportRequestButton(ServerTemplateRuntime runtime, ServerCommandService commands, LongSupplier clock) {
		this(runtime, commands, clock, () -> { });
	}

	TeleportRequestButton(ServerTemplateRuntime runtime, ServerCommandService commands, LongSupplier clock,
			Runnable soundPlayer) {
		this.runtime = runtime;
		this.commands = commands;
		this.clock = clock;
		this.soundPlayer = soundPlayer;
	}

	public void handleMessage(String message) {
		ActiveTemplateSnapshot snapshot = runtime.activeSnapshot().orElse(null);
		if (snapshot == null || !commands.supports(CommandTemplateValidator.CommandType.ACCEPT_TELEPORT)) {
			return;
		}
		Matcher matcher = runtime.compiledParsers().flatMap(CompiledParserSettings::teleportRequest)
				.map(pattern -> pattern.matcher(ChatMessageTextSanitizer.stripSyntheticLabels(message).trim()))
				.orElse(null);
		if (matcher == null || !matcher.find()) {
			return;
		}
		String requester = matcher.group(1);
		if (!PlayerNameValidator.validate(requester).valid()) {
			return;
		}
		pending = new PendingRequest(requester, snapshot.generation(), clock.getAsLong() + TIMEOUT_MILLIS);
		playSound = true;
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.getConnection() == null || !visible()) {
			pending = null;
			playSound = false;
			return;
		}
		playPendingSound();
	}

	public boolean click(double mouseX, double mouseY, int screenWidth, Font font) {
		PendingRequest request = current();
		if (request == null) {
			return false;
		}
		Bounds bounds = bounds(screenWidth, font, request.requester());
		if (!bounds.contains(mouseX, mouseY)) {
			return false;
		}
		pending = null;
		commands.acceptTeleport();
		return true;
	}

	public void resetRuntimeState() {
		pending = null;
		playSound = false;
	}

	public void register() {
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(CndlChatPlusClient.MOD_ID, "teleport_request"),
				(graphics, deltaTracker) -> render(new CompatGraphics(graphics)));
	}

	boolean visible() {
		return current() != null;
	}

	String requester() {
		PendingRequest request = current();
		return request == null ? null : request.requester();
	}

	void playPendingSound() {
		if (!playSound) {
			return;
		}
		playSound = false;
		soundPlayer.run();
	}

	private void render(CompatGraphics graphics) {
		PendingRequest request = current();
		if (request == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		String label = label(request);
		Bounds bounds = bounds(graphics.guiWidth(), minecraft.font, request.requester());
		graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + HEIGHT,
				UiConstants.SURFACE_ALT);
		graphics.outline(bounds.x(), bounds.y(), bounds.width(), HEIGHT, UiConstants.SUCCESS);
		graphics.centeredText(minecraft.font, label, bounds.x() + bounds.width() / 2,
				bounds.y() + 6, UiConstants.TEXT);
	}

	private PendingRequest current() {
		PendingRequest request = pending;
		if (request == null || request.expiresAt() <= clock.getAsLong()) {
			return null;
		}
		long generation = runtime.activeSnapshot().map(ActiveTemplateSnapshot::generation).orElse(-1L);
		return request.generation() == generation ? request : null;
	}

	private String label(PendingRequest request) {
		long seconds = Math.max(1L, (request.expiresAt() - clock.getAsLong() + 999L) / 1_000L);
		return "Принять ТП от " + request.requester() + " (" + seconds + ")";
	}

	private static Bounds bounds(int screenWidth, Font font, String requester) {
		int width = Math.min(screenWidth - 4, font.width("Принять ТП от " + requester + " (60)") + 16);
		return new Bounds((screenWidth - width) / 2, 8, width);
	}

	private record PendingRequest(String requester, long generation, long expiresAt) { }

	private record Bounds(int x, int y, int width) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT;
		}
	}
}
