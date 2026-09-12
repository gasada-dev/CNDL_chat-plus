package ru.gasada.cndlchatplus.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.gasada.cndlchatplus.ChatSearchState;
import ru.gasada.cndlchatplus.ChatContextMenuController;
import ru.gasada.cndlchatplus.ChatMessageTextSanitizer;
import ru.gasada.cndlchatplus.ChatTabBar;
import ru.gasada.cndlchatplus.ChatTabController;
import ru.gasada.cndlchatplus.ChatTabFilterAccess;
import ru.gasada.cndlchatplus.ChatTextSelection;
import ru.gasada.cndlchatplus.ChatTextSelectionAccess;
import ru.gasada.cndlchatplus.CompatGraphics;
import ru.gasada.cndlchatplus.CndlChatPlusClient;
import ru.gasada.cndlchatplus.ScreenWidgetAccess;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow protected EditBox input;

	private EditBox gasada$searchBox;
	private final ChatContextMenuController gasada$contextMenu = new ChatContextMenuController();
	private ChatTextSelection.Point gasada$selectionStart;
	private ChatTextSelection.Point gasada$selectionEnd;
	private List<String> gasada$selectionLines;
	private List<int[]> gasada$selectionAdvances;
	private int gasada$selectionScrollPosition;
	private double gasada$selectionStartX;
	private double gasada$selectionStartY;
	private boolean gasada$selectionDragged;

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void gasada$renderTabs(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return;
		gasada$renderSelection(graphics, mouseX, mouseY);
		ChatTabController tabs = CndlChatPlusClient.CHAT_TABS;
		Screen screen = (Screen) (Object) this;
		CompatGraphics compat = new CompatGraphics(graphics);
		if (tabs != null && tabs.enabled()) {
			ChatTabBar.render(compat, Minecraft.getInstance().font, tabs,
					screen.width, screen.height, mouseX, mouseY, Minecraft.getInstance());
		}
		ChatTabBar.renderSearchHint(compat, Minecraft.getInstance().font,
				CndlChatPlusClient.CHAT_SEARCH, screen.height, Minecraft.getInstance());
		ChatTabBar.renderBookmarks(compat, Minecraft.getInstance().font,
				screen.width, screen.height, mouseX, mouseY, Minecraft.getInstance());
		gasada$contextMenu.render(compat, mouseX, mouseY, Minecraft.getInstance());
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private void gasada$tabClick(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> cir) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return;
		Screen screen = (Screen) (Object) this;
		if (event.button() == 0 && CndlChatPlusClient.TELEPORT_REQUEST != null
				&& CndlChatPlusClient.TELEPORT_REQUEST.click(event.x(), event.y(), screen.width,
						Minecraft.getInstance().font)) {
			cir.setReturnValue(true);
			return;
		}
		if (event.button() == 1 && CndlChatPlusClient.MARRIAGE_HUD != null
				&& CndlChatPlusClient.MARRIAGE_HUD.rightClick(event.x(), event.y(), screen.width,
						screen.height, Minecraft.getInstance().font, Minecraft.getInstance())) {
			gasada$contextMenu.close();
			cir.setReturnValue(true);
			return;
		}
		if (event.button() == 1 && gasada$contextMenu.rightClick(event.x(), event.y(),
				screen.width, screen.height, Minecraft.getInstance())) {
			cir.setReturnValue(true);
			return;
		}
		if (gasada$contextMenu.open()) {
			if (event.button() == 0) {
				gasada$contextMenu.leftClick(event.x(), event.y(), screen,
						value -> gasada$setInput(screen, value), Minecraft.getInstance());
			} else {
				gasada$contextMenu.close();
			}
			cir.setReturnValue(true);
			return;
		}
		if (event.button() == 0 && ChatTabBar.clickBookmarks(screen, Minecraft.getInstance().font,
				screen.width, screen.height, event.x(), event.y(), Minecraft.getInstance())) {
			cir.setReturnValue(true);
			return;
		}
		if (event.button() == 0) {
			gasada$startSelection(event.x(), event.y(), screen.height);
		}
		ChatTabController tabs = CndlChatPlusClient.CHAT_TABS;
		if (tabs == null || !tabs.enabled() || event.button() != 0) {
			return;
		}
		if (ChatTabBar.click(tabs, Minecraft.getInstance().font, screen.width, screen.height,
				event.x(), event.y(), Minecraft.getInstance())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "init()V", at = @At("TAIL"), require = 0)
	private void gasada$chatOpened(CallbackInfo ci) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return;
		ChatTabController tabs = CndlChatPlusClient.CHAT_TABS;
		if (tabs != null) {
			tabs.chatOpened();
		}
		ChatSearchState search = CndlChatPlusClient.CHAT_SEARCH;
		if (search != null && search.enabled()) {
			if (search.active()) {
				search.clear();
				gasada$refreshChat();
			}
			Screen screen = (Screen) (Object) this;
			int y = ChatTabBar.searchBoxY(Minecraft.getInstance(), screen.height);
			gasada$searchBox = new EditBox(Minecraft.getInstance().font, 2, y,
					ChatTabBar.searchBoxWidth(Minecraft.getInstance().font, screen.width), 18,
					Component.literal("Поиск по чату"));
			gasada$searchBox.setHint(Component.literal("Поиск по чату"));
			gasada$searchBox.setResponder(value -> {
				search.setQuery(value);
				gasada$refreshChat();
			});
			gasada$searchBox.setVisible(false);
			((ScreenWidgetAccess) screen).gasada$addRenderableWidget(gasada$searchBox);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
	private void gasada$searchKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return;
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && gasada$contextMenu.close()) {
			cir.setReturnValue(true);
			return;
		}
		ChatSearchState search = CndlChatPlusClient.CHAT_SEARCH;
		if (search == null || !search.enabled() || gasada$searchBox == null) {
			return;
		}
		Screen screen = (Screen) (Object) this;
		if (event.key() == GLFW.GLFW_KEY_F && event.hasControlDown()) {
			search.activate();
			gasada$searchBox.setVisible(true);
			screen.setFocused(gasada$searchBox);
			gasada$searchBox.setFocused(true);
			cir.setReturnValue(true);
			return;
		}
		if (!gasada$searchBox.isFocused()) {
			return;
		}
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			gasada$closeSearch(screen);
			cir.setReturnValue(true);
			return;
		}
		gasada$searchBox.keyPressed(event);
		cir.setReturnValue(true);
	}

	@Inject(method = "removed", at = @At("TAIL"), require = 0)
	private void gasada$chatClosed(CallbackInfo ci) {
		if (!CndlChatPlusClient.CONNECTION_GATE.active()) return;
		gasada$clearSelection();
		gasada$contextMenu.close();
		ChatTabController tabs = CndlChatPlusClient.CHAT_TABS;
		if (tabs != null) {
			tabs.chatClosed();
		}
		ChatSearchState search = CndlChatPlusClient.CHAT_SEARCH;
		if (search != null && search.active()) {
			search.clear();
			gasada$refreshChat();
		}
	}

	private void gasada$closeSearch(Screen screen) {
		CndlChatPlusClient.CHAT_SEARCH.clear();
		gasada$searchBox.setValue("");
		gasada$searchBox.setVisible(false);
		screen.setFocused(input);
		input.setFocused(true);
		gasada$refreshChat();
	}

	private void gasada$refreshChat() {
		CndlChatPlusClient.CHAT_TABS.refresh(Minecraft.getInstance());
	}

	private void gasada$setInput(Screen screen, String value) {
		if (CndlChatPlusClient.CHAT_SEARCH != null && CndlChatPlusClient.CHAT_SEARCH.active()) {
			gasada$closeSearch(screen);
		}
		input.setValue(value);
		screen.setFocused(input);
		input.setFocused(true);
	}

	private void gasada$startSelection(double mouseX, double mouseY, int screenHeight) {
		ChatTabFilterAccess metrics = (ChatTabFilterAccess) Minecraft.getInstance().gui.getChat();
		if (!ChatTextSelection.contains(mouseX, mouseY, screenHeight, metrics.gasada$chatWidth(),
				metrics.gasada$chatHeight(), metrics.gasada$chatScale(), metrics.gasada$chatLineHeight())) {
			return;
		}
		ChatTextSelectionAccess access = (ChatTextSelectionAccess) Minecraft.getInstance().gui.getChat();
		List<String> lines = access.gasada$selectionLines();
		if (lines.isEmpty()) return;
		List<int[]> advances = gasada$characterAdvances(lines);
		ChatTextSelection.Point point = ChatTextSelection.pointAt(mouseX, mouseY, screenHeight,
				metrics.gasada$chatHeight(), metrics.gasada$chatScale(), metrics.gasada$chatLineHeight(),
				access.gasada$selectionScrollPosition(), lines, advances);
		if (point == null) return;
		gasada$selectionStart = point;
		gasada$selectionEnd = point;
		gasada$selectionLines = lines;
		gasada$selectionAdvances = advances;
		gasada$selectionScrollPosition = access.gasada$selectionScrollPosition();
		gasada$selectionStartX = mouseX;
		gasada$selectionStartY = mouseY;
		gasada$selectionDragged = false;
	}

	private void gasada$renderSelection(GuiGraphics graphics, int mouseX, int mouseY) {
		if (gasada$selectionStart == null) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
				!= GLFW.GLFW_PRESS) {
			gasada$copySelection(minecraft);
			return;
		}
		ChatTabFilterAccess metrics = (ChatTabFilterAccess) minecraft.gui.getChat();
		gasada$selectionEnd = ChatTextSelection.pointAt(mouseX, mouseY, ((Screen) (Object) this).height,
				metrics.gasada$chatHeight(), metrics.gasada$chatScale(), metrics.gasada$chatLineHeight(),
				gasada$selectionScrollPosition, gasada$selectionLines, gasada$selectionAdvances);
		gasada$selectionDragged |= gasada$dragged(mouseX, mouseY);
		if (!gasada$selectionDragged || gasada$selectionEnd == null) return;
		ChatTextSelection.Point top = gasada$selectionStart.line() >= gasada$selectionEnd.line()
				? gasada$selectionStart : gasada$selectionEnd;
		ChatTextSelection.Point bottom = top == gasada$selectionStart ? gasada$selectionEnd : gasada$selectionStart;
		double scale = metrics.gasada$chatScale();
		int lineHeight = metrics.gasada$chatLineHeight();
		for (int line = top.line(); line >= bottom.line(); line--) {
			String text = gasada$selectionLines.get(line);
			int start = line == bottom.line() ? bottom.offset() : 0;
			int end = line == top.line() ? top.offset() : text.length();
			if (start == end) continue;
			int[] advances = gasada$selectionAdvances.get(line);
			int left = (int) Math.floor((4 + advances[start]) * scale);
			int right = (int) Math.ceil((4 + advances[end]) * scale);
			int y = ChatTextSelection.lineTop(((Screen) (Object) this).height, scale, lineHeight,
					gasada$selectionScrollPosition, line);
			graphics.fill(left, y, right, y + (int) Math.ceil(lineHeight * scale), 0x664a90e2);
		}
	}

	private List<int[]> gasada$characterAdvances(List<String> lines) {
		List<int[]> advances = new ArrayList<>(lines.size());
		for (String text : lines) {
			int[] widths = new int[text.length() + 1];
			for (int offset = 1; offset < widths.length; offset++) {
				widths[offset] = Minecraft.getInstance().font.width(text.substring(0, offset));
			}
			advances.add(widths);
		}
		return advances;
	}

	private boolean gasada$dragged(int mouseX, int mouseY) {
		double x = mouseX - gasada$selectionStartX;
		double y = mouseY - gasada$selectionStartY;
		return x * x + y * y >= 16;
	}

	private void gasada$copySelection(Minecraft minecraft) {
		if (gasada$selectionDragged) {
			String selected = ChatMessageTextSanitizer.stripDisplayFormatting(
					ChatTextSelection.selectedText(gasada$selectionLines, gasada$selectionStart, gasada$selectionEnd));
			if (!selected.isBlank()) {
				minecraft.keyboardHandler.setClipboard(selected);
				minecraft.gui.getChat().addMessage(Component.literal("Скопировано: " + selected.length() + " символов"));
			}
		}
		gasada$clearSelection();
	}

	private void gasada$clearSelection() {
		gasada$selectionStart = null;
		gasada$selectionEnd = null;
		gasada$selectionLines = null;
		gasada$selectionAdvances = null;
		gasada$selectionDragged = false;
	}
}
