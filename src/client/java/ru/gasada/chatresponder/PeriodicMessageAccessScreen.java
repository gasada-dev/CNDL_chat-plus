package ru.gasada.chatresponder;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PeriodicMessageAccessScreen extends CompatScreen {
	private static final String PASSWORD = "1239";
	private static final int PANEL_WIDTH = 280;
	private static final int PANEL_HEIGHT = 130;

	private final Screen parent;
	private final ResponderConfig config;
	private EditBox passwordBox;
	private String statusText = "";

	public PeriodicMessageAccessScreen(Screen parent, ResponderConfig config) {
		super(Component.literal("Доступ к автосообщениям"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		int panelX = (width - PANEL_WIDTH) / 2;
		int panelY = (height - PANEL_HEIGHT) / 2;
		passwordBox = new EditBox(font, panelX + 50, panelY + 48, 180, 20,
				Component.literal("Пароль"));
		passwordBox.setMaxLength(PASSWORD.length());
		passwordBox.setHint(Component.literal("Пароль"));
		addRenderableWidget(passwordBox);

		addRenderableWidget(Button.builder(Component.literal("Открыть"), ignored -> unlock())
				.bounds(panelX + 50, panelY + 82, 85, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Назад"), ignored -> onClose())
				.bounds(panelX + 145, panelY + 82, 85, 20).build());
	}

	private void unlock() {
		if (acceptsPassword(passwordBox.getValue())) {
			ClientUi.setScreen(minecraft, new PeriodicMessageScreen(parent, config));
			return;
		}
		statusText = "Неверный пароль";
		passwordBox.setValue("");
	}

	static boolean acceptsPassword(String password) {
		return PASSWORD.equals(password);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isSubmitKey(event.key())) {
			unlock();
			return true;
		}
		return super.keyPressed(event);
	}

	static boolean isSubmitKey(int key) {
		return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE010141D);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		int panelX = (width - PANEL_WIDTH) / 2;
		int panelY = (height - PANEL_HEIGHT) / 2;
		graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2,
				0xFF536178);
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xF0242B38);
		graphics.centeredText(font, title, width / 2, panelY + 16, 0xFFE8ECF2);
		if (!statusText.isEmpty()) {
			graphics.centeredText(font, statusText, width / 2, panelY + 110, 0xFFFF7777);
		}
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
