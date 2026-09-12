package ru.gasada.cndlchatplus;

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

	MarriageHud(VanillaBoxRuntime runtime, PlayerInfoService playerInfo,
			ServerCommandService commands, FriendsHud friendsHud) {
		this.runtime = runtime;
		this.commands = commands;
		this.friendsHud = friendsHud;
		controller = new MarriageHudController(playerInfo);
	}

	public void tick(Minecraft minecraft) {
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
		int contentWidth = Math.max(font.width(TITLE), font.width(snapshot.partner()));
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
		int contentWidth = Math.max(font.width(TITLE), font.width(snapshot.partner()));
		Bounds bounds = bounds(graphics.guiWidth(), graphics.guiHeight(), contentWidth,
				friendsHud.occupiedHeight(), friendsHud.renderedWidth());
		graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
				UiConstants.MARRIAGE_SURFACE);
		graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), UiConstants.MARRIAGE_BORDER);
		graphics.text(font, TITLE, bounds.x() + 6, bounds.y() + 4, UiConstants.MARRIAGE_ACCENT);
		graphics.text(font, snapshot.partner(), bounds.x() + 6, bounds.y() + 15, UiConstants.MARRIAGE_TEXT);
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
}
