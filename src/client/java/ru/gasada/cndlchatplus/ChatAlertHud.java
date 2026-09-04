package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class ChatAlertHud {
	static final long NOTICE_MILLIS = 4_000L;
	private static final int MAX_NOTICES = 3;
	private static final int MAX_TEXT_LENGTH = 180;
	private final LongSupplier clock;
	private final Runnable soundPlayer;
	private volatile List<Notice> notices = List.of();
	private int pendingSounds;

	public ChatAlertHud() {
		this(System::currentTimeMillis, () -> Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.75F)));
	}

	ChatAlertHud(LongSupplier clock, Runnable soundPlayer) {
		this.clock = clock;
		this.soundPlayer = soundPlayer;
	}

	public void handle(ChatAlertDecision decision, String message) {
		if (!decision.triggered()) return;
		if (decision.soundEnabled()) pendingSounds++;
		if (!decision.hudEnabled()) return;
		String text = ChatMessageTextSanitizer.stripSyntheticLabels(message).trim();
		if (text.length() > MAX_TEXT_LENGTH) text = text.substring(0, MAX_TEXT_LENGTH - 3) + "...";
		List<Notice> updated = new ArrayList<>(notices);
		updated.add(new Notice(text, clock.getAsLong() + NOTICE_MILLIS));
		if (updated.size() > MAX_NOTICES) updated.removeFirst();
		notices = List.copyOf(updated);
	}

	public void tick(Minecraft minecraft) {
		tickRuntime(minecraft.getConnection() != null);
	}

	void tickRuntime(boolean connected) {
		if (!connected) {
			resetRuntimeState();
			return;
		}
		long now = clock.getAsLong();
		notices = notices.stream().filter(notice -> notice.expiresAt() > now).toList();
		for (int count = pendingSounds; count > 0; count--) soundPlayer.run();
		pendingSounds = 0;
	}

	List<String> noticeTexts() {
		return notices.stream().map(Notice::text).toList();
	}

	public void resetRuntimeState() {
		notices = List.of();
		pendingSounds = 0;
	}

	public void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(CndlChatPlusClient.MOD_ID, "chat_alerts"),
				(graphics, deltaTracker) -> render(new CompatGraphics(graphics), notices));
	}

	private void render(CompatGraphics graphics, List<Notice> snapshot) {
		if (snapshot.isEmpty()) return;
		Font font = Minecraft.getInstance().font;
		int maxWidth = Math.max(40, Math.min(360, graphics.guiWidth() - 10));
		int y = 8;
		for (Notice notice : snapshot) {
			String text = font.plainSubstrByWidth(notice.text(), maxWidth - 12);
			int width = font.width(text) + 12;
			int x = graphics.guiWidth() - width - 5;
			graphics.fill(x, y, x + width, y + 18, 0xD0222937);
			graphics.outline(x, y, width, 18, UiConstants.ACCENT);
			graphics.text(font, text, x + 6, y + 5, UiConstants.TEXT);
			y += 21;
		}
	}

	record Notice(String text, long expiresAt) {
	}
}
