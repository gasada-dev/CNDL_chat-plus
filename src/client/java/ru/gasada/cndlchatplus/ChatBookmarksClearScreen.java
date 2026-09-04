package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChatBookmarksClearScreen extends CompatScreen {
	private final Screen parent;
	private final ChatBookmarkStore store;
	private final Runnable cleared;

	public ChatBookmarksClearScreen(Screen parent, ChatBookmarkStore store, Runnable cleared) {
		super(Component.literal("Очистить все закладки?"));
		this.parent = parent;
		this.store = store;
		this.cleared = cleared;
	}

	@Override
	protected void init() {
		int x = width / 2 - 104;
		int y = height / 2 + 16;
		addRenderableWidget(StyledButton.create(Component.literal("Да, очистить"), ignored -> {
			store.clear();
			cleared.run();
			onClose();
		}).bounds(x, y, 100, FIELD_HEIGHT).build());
		addRenderableWidget(StyledButton.create(Component.literal("Отмена"), ignored -> onClose())
				.bounds(x + 108, y, 100, FIELD_HEIGHT).build());
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.centeredText(font, title, width / 2, height / 2 - 18, WARNING);
		graphics.centeredText(font, "Действие нельзя отменить", width / 2, height / 2, MUTED);
	}

	@Override
	public void onClose() {
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
