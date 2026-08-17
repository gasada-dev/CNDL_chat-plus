package ru.gasada.chatresponder.mixin;

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
import ru.gasada.chatresponder.ChatSearchState;
import ru.gasada.chatresponder.ChatContextMenuController;
import ru.gasada.chatresponder.ChatTabBar;
import ru.gasada.chatresponder.ChatTabController;
import ru.gasada.chatresponder.CompatGraphics;
import ru.gasada.chatresponder.GasadaChatResponderClient;
import ru.gasada.chatresponder.ScreenWidgetAccess;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow protected EditBox input;

	private EditBox gasada$searchBox;
	private final ChatContextMenuController gasada$contextMenu = new ChatContextMenuController();

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void gasada$renderTabs(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ChatTabController tabs = GasadaChatResponderClient.CHAT_TABS;
		Screen screen = (Screen) (Object) this;
		CompatGraphics compat = new CompatGraphics(graphics);
		if (tabs != null && tabs.enabled()) {
			ChatTabBar.render(compat, Minecraft.getInstance().font, tabs,
					screen.width, screen.height, mouseX, mouseY, Minecraft.getInstance());
		}
		ChatTabBar.renderSearchHint(compat, Minecraft.getInstance().font,
				GasadaChatResponderClient.CHAT_SEARCH, screen.height, Minecraft.getInstance());
		gasada$contextMenu.render(compat, mouseX, mouseY, Minecraft.getInstance());
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private void gasada$tabClick(MouseButtonEvent event, boolean doubleClick,
			CallbackInfoReturnable<Boolean> cir) {
		Screen screen = (Screen) (Object) this;
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
		ChatTabController tabs = GasadaChatResponderClient.CHAT_TABS;
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
		ChatTabController tabs = GasadaChatResponderClient.CHAT_TABS;
		if (tabs != null) {
			tabs.chatOpened();
		}
		ChatSearchState search = GasadaChatResponderClient.CHAT_SEARCH;
		if (search != null && search.enabled()) {
			if (search.active()) {
				search.clear();
				gasada$refreshChat();
			}
			Screen screen = (Screen) (Object) this;
			int y = ChatTabBar.searchBoxY(Minecraft.getInstance(), screen.height);
			gasada$searchBox = new EditBox(Minecraft.getInstance().font, 2, y,
					Math.min(240, screen.width - 4), 18, Component.literal("Поиск по чату"));
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
		if (event.key() == GLFW.GLFW_KEY_ESCAPE && gasada$contextMenu.close()) {
			cir.setReturnValue(true);
			return;
		}
		ChatSearchState search = GasadaChatResponderClient.CHAT_SEARCH;
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
		gasada$contextMenu.close();
		ChatTabController tabs = GasadaChatResponderClient.CHAT_TABS;
		if (tabs != null) {
			tabs.chatClosed();
		}
		ChatSearchState search = GasadaChatResponderClient.CHAT_SEARCH;
		if (search != null && search.active()) {
			search.clear();
			gasada$refreshChat();
		}
	}

	private void gasada$closeSearch(Screen screen) {
		GasadaChatResponderClient.CHAT_SEARCH.clear();
		gasada$searchBox.setValue("");
		gasada$searchBox.setVisible(false);
		screen.setFocused(input);
		input.setFocused(true);
		gasada$refreshChat();
	}

	private void gasada$refreshChat() {
		GasadaChatResponderClient.CHAT_TABS.refresh(Minecraft.getInstance());
	}

	private void gasada$setInput(Screen screen, String value) {
		if (GasadaChatResponderClient.CHAT_SEARCH != null && GasadaChatResponderClient.CHAT_SEARCH.active()) {
			gasada$closeSearch(screen);
		}
		input.setValue(value);
		screen.setFocused(input);
		input.setFocused(true);
	}
}
