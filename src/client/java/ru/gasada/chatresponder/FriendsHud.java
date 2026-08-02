package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class FriendsHud {
	private static final long ONLINE_NOTICE_MS = 4_000;
	private static final long CONNECTION_WARMUP_MS = 30_000;
	private static final long OFFLINE_CONFIRM_MS = 5_000;
	private static final Set<String> previousOnline = new HashSet<>();
	private static final Map<String, Long> offlineSince = new LinkedHashMap<>();
	private static final Map<String, Long> onlineNotices = new LinkedHashMap<>();
	private static boolean notificationsArmed;
	private static ClientPacketListener activeConnection;
	private static long notificationsEnabledAt;

	private FriendsHud() {
	}

	public static void resetRuntimeState() {
		previousOnline.clear();
		offlineSince.clear();
		onlineNotices.clear();
		notificationsArmed = false;
		activeConnection = null;
		notificationsEnabledAt = 0L;
	}

	public static void register(ResponderConfig config) {
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(GasadaChatResponderClient.MOD_ID, "online_friends"),
				(graphics, deltaTracker) -> render(graphics, config));
	}

	private static void render(GuiGraphicsExtractor graphics, ResponderConfig config) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() == null) {
			resetRuntimeState();
			return;
		}
		long now = System.currentTimeMillis();
		if (activeConnection != minecraft.getConnection()) {
			activeConnection = minecraft.getConnection();
			previousOnline.clear();
			offlineSince.clear();
			onlineNotices.clear();
			notificationsArmed = false;
			notificationsEnabledAt = now + CONNECTION_WARMUP_MS;
		}

		List<String> online = new ArrayList<>();
		Set<String> currentOnline = new HashSet<>();
		for (String friend : config.friends) {
			if (minecraft.getConnection().getPlayerInfoIgnoreCase(friend) != null) {
				online.add(friend);
				currentOnline.add(friend.toLowerCase(Locale.ROOT));
			}
		}

		boolean friendCameOnline = false;
		if (!notificationsArmed && now >= notificationsEnabledAt) {
			notificationsArmed = true;
			for (String friend : config.friends) {
				String normalized = friend.toLowerCase(Locale.ROOT);
				if (!currentOnline.contains(normalized)) {
					offlineSince.put(normalized, now);
				}
			}
		} else if (notificationsArmed) {
			for (String friend : config.friends) {
				String normalized = friend.toLowerCase(Locale.ROOT);
				if (!currentOnline.contains(normalized)) {
					offlineSince.putIfAbsent(normalized, now);
					continue;
				}
				Long confirmedOfflineAt = offlineSince.remove(normalized);
				if (!previousOnline.contains(normalized) && confirmedOfflineAt != null
						&& now - confirmedOfflineAt >= OFFLINE_CONFIRM_MS) {
					onlineNotices.remove(friend);
					onlineNotices.put(friend, now + ONLINE_NOTICE_MS);
					friendCameOnline = true;
				}
			}
		}
		previousOnline.clear();
		previousOnline.addAll(currentOnline);
		onlineNotices.entrySet().removeIf(entry -> entry.getValue() <= now);
		if (friendCameOnline && Boolean.TRUE.equals(config.friendHudEnabled)) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					SoundEvents.PLAYER_LEVELUP, 1.0F, 0.75F));
		}

		if (!Boolean.TRUE.equals(config.friendHudEnabled)) {
			onlineNotices.clear();
			return;
		}
		if (online.isEmpty()) {
			return;
		}

		Font font = minecraft.font;
		String title = "Друзья онлайн: " + online.size();
		int contentWidth = font.width(title);
		for (String friend : online) {
			contentWidth = Math.max(contentWidth, font.width("● " + friend));
		}

		int boxWidth = contentWidth + 12;
		int boxHeight = 8 + (online.size() + 1) * 11;
		int x = graphics.guiWidth() - boxWidth - 5;
		int y = graphics.guiHeight() - boxHeight - 5;
		graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xB0181D27);
		graphics.outline(x, y, boxWidth, boxHeight, 0xCC536178);
		graphics.text(font, title, x + 6, y + 4, 0xFFE8ECF2);
		for (int index = 0; index < online.size(); index++) {
			graphics.text(font, "● " + online.get(index), x + 6, y + 15 + index * 11, 0xFF55FF55);
		}

		int noticeY = y - 5;
		float noticeScale = 1.15F;
		List<String> notices = new ArrayList<>(onlineNotices.keySet());
		for (int index = notices.size() - 1; index >= 0; index--) {
			String notice = notices.get(index) + " в сети!";
			int noticeWidth = Math.round(font.width(notice) * noticeScale) + 16;
			noticeY -= 20;
			int noticeX = graphics.guiWidth() - noticeWidth - 5;
			graphics.fill(noticeX, noticeY, noticeX + noticeWidth, noticeY + 18, 0xD0222937);
			graphics.outline(noticeX, noticeY, noticeWidth, 18, 0xD0A242F3);
			graphics.pose().pushMatrix();
			graphics.pose().translate(noticeX + 8, noticeY + 4);
			graphics.pose().scale(noticeScale);
			graphics.text(font, notice, 0, 0, 0xFF55FF55);
			graphics.pose().popMatrix();
		}
	}
}
