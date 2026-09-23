package ru.gasada.cndlchatplus;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

public final class MarriageHud {
	private static final String TITLE = "Брак";
	private static final int HEIGHT = 30;

	private final VanillaBoxRuntime runtime;
	private final ServerCommandService commands;
	private final FriendsHud friendsHud;
	private final MarriageHudController controller;
	private volatile Set<String> onlinePlayers = Set.of();

	MarriageHud(VanillaBoxRuntime runtime, PlayerInfoService playerInfo,
			ServerCommandService commands, FriendsHud friendsHud) {
		this.runtime = runtime;
		this.commands = commands;
		this.friendsHud = friendsHud;
		controller = new MarriageHudController(playerInfo);
	}

	public void tick(Minecraft minecraft) {
		Set<String> players = new HashSet<>();
		if (minecraft.getConnection() != null) {
			minecraft.getConnection().getListedOnlinePlayers().forEach(info ->
					players.add(info.getProfile().name().toLowerCase(Locale.ROOT)));
		}
		onlinePlayers = players;
		long generation = runtime.activeSnapshot().map(VanillaBoxSnapshot::generation).orElse(-1L);
		controller.tick(minecraft.getConnection(), generation, PlatformBridgeNetworking.available(),
				() -> minecraft.getUser().getName());
	}

	public void resetRuntimeState() {
		controller.resetRuntimeState();
	}

	public void register() {
		HudElementRegistry.addLast(
				Identifier.fromNamespaceAndPath(CndlChatPlusClient.MOD_ID, "marriage"),
				(graphics, deltaTracker) -> render(new CompatGraphics(graphics), controller.snapshot()));
	}

	public boolean rightClick(double mouseX, double mouseY, int screenWidth, int screenHeight,
			Font font, Minecraft minecraft) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return false;
		MarriageHudSnapshot snapshot = controller.snapshot();
		if (!snapshot.visible()) return false;
		PartnerStatus status = partnerStatus(snapshot.partner(), onlinePlayers);
		int contentWidth = Math.max(font.width(TITLE), font.width(status.line(snapshot.partner())));
		Bounds bounds = bounds(screenWidth, screenHeight, contentWidth, friendsHud.occupiedHeight(),
				friendsHud.renderedWidth());
		if (!bounds.contains(mouseX, mouseY)) return false;
		ClientUi.setScreen(minecraft, new MarriageMenuScreen(snapshot.context()));
		return true;
	}

	MarriageHudController.ActionResult execute(MarriageHudSnapshot.Context context, MarriageAction action) {
		return controller.execute(context, action, commands);
	}

	private void render(CompatGraphics graphics, MarriageHudSnapshot snapshot) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active() || !snapshot.visible()) return;
		Font font = Minecraft.getInstance().font;
		PartnerStatus status = partnerStatus(snapshot.partner(), onlinePlayers);
		int contentWidth = Math.max(font.width(TITLE), font.width(status.line(snapshot.partner())));
		Bounds bounds = bounds(graphics.guiWidth(), graphics.guiHeight(), contentWidth,
				friendsHud.occupiedHeight(), friendsHud.renderedWidth());
		graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
				ThemeTokens.marriageSurface());
		graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), ThemeTokens.marriageBorder());
		graphics.text(font, TITLE, bounds.x() + 6, bounds.y() + 4, ThemeTokens.marriageAccent());
		graphics.text(font, snapshot.partner(), bounds.x() + 6, bounds.y() + 15, ThemeTokens.marriageText());
		if (!status.text().isEmpty()) {
			graphics.text(font, status.text(), bounds.x() + 10 + font.width(snapshot.partner()),
					bounds.y() + 15, status.color());
		}
	}

	static PartnerStatus partnerStatus(String partner, Set<String> onlinePlayers) {
		return onlinePlayers.contains(partner.toLowerCase(Locale.ROOT))
				? new PartnerStatus("", ThemeTokens.textMuted())
				: new PartnerStatus("оффлайн", ThemeTokens.textMuted());
	}

	static Bounds bounds(int screenWidth, int screenHeight, int contentWidth, int friendsOccupiedHeight,
			int friendsWidth) {
		int width = Math.max(contentWidth + 12, friendsWidth);
		return new Bounds(screenWidth - width - 5,
				screenHeight - friendsOccupiedHeight - HEIGHT - 5, width, HEIGHT);
	}

	record Bounds(int x, int y, int width, int height) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	record PartnerStatus(String text, int color) {
		String line(String partner) {
			return text.isEmpty() ? partner : partner + " " + text;
		}
	}
}
