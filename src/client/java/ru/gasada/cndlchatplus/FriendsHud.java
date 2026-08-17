package ru.gasada.cndlchatplus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public final class FriendsHud {
	private final ServerTemplateRuntime runtime;
	private final FriendPresenceTracker tracker = new FriendPresenceTracker();
	private volatile FriendHudSnapshot snapshot = FriendHudSnapshot.empty();

	public FriendsHud(ServerTemplateRuntime runtime) {
		this.runtime = runtime;
	}

	public void resetRuntimeState() {
		tracker.reset();
		snapshot = FriendHudSnapshot.empty();
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.getConnection() == null) {
			resetRuntimeState();
			return;
		}
		Set<String> onlinePlayers = new HashSet<>();
		minecraft.getConnection().getListedOnlinePlayers().forEach(info ->
				onlinePlayers.add(info.getProfile().name()));
		snapshot = tracker.update(runtime.activeSnapshot().orElse(null), onlinePlayers,
				minecraft.getConnection(), System.currentTimeMillis());
		if (snapshot.playSound()) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
					SoundEvents.PLAYER_LEVELUP, 1.0F, 0.75F));
		}
	}

	public void register() {
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(CndlChatPlusClient.MOD_ID, "online_friends"),
				(graphics, deltaTracker) -> render(new CompatGraphics(graphics), snapshot));
	}

	private static void render(CompatGraphics graphics, FriendHudSnapshot snapshot) {
		if (!snapshot.hudEnabled() || snapshot.onlineFriends().isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		List<String> online = snapshot.onlineFriends();
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
		graphics.outline(x, y, boxWidth, boxHeight, UiConstants.BORDER);
		graphics.text(font, title, x + 6, y + 4, UiConstants.TEXT);
		for (int index = 0; index < online.size(); index++) {
			graphics.text(font, "● " + online.get(index), x + 6, y + 15 + index * 11, UiConstants.ONLINE);
		}

		int noticeY = y - 5;
		float noticeScale = 1.15F;
		List<String> notices = snapshot.notices();
		for (int index = notices.size() - 1; index >= 0; index--) {
			String notice = notices.get(index) + " в сети!";
			int noticeWidth = Math.round(font.width(notice) * noticeScale) + 16;
			noticeY -= 20;
			int noticeX = graphics.guiWidth() - noticeWidth - 5;
			graphics.fill(noticeX, noticeY, noticeX + noticeWidth, noticeY + 18, 0xD0222937);
			graphics.outline(noticeX, noticeY, noticeWidth, 18, UiConstants.ACCENT);
			graphics.pushPose();
			graphics.translatePose(noticeX + 8, noticeY + 4);
			graphics.scalePose(noticeScale);
			graphics.text(font, notice, 0, 0, UiConstants.ONLINE);
			graphics.popPose();
		}
	}
}
