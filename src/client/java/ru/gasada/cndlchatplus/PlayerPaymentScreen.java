package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.ThemeTokens.*;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

final class PlayerPaymentScreen extends CompatScreen {
	private static final int PANEL_HEIGHT = 176;

	private final Screen parent;
	private final String player;
	private EditBox amountBox;
	private String amountValue = "";
	private String status = "";
	private int panelX;
	private int panelY;
	private int panelWidth;

	PlayerPaymentScreen(Screen parent, String player) {
		super(Component.literal("Перевод денег"));
		this.parent = parent;
		this.player = player;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(260, width - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - PANEL_HEIGHT) / 2;
		amountBox = new StyledEditBox(font, panelX + 16, panelY + 56, panelWidth - 32,
				FIELD_HEIGHT, Component.literal("Сумма перевода"));
		amountBox.setMaxLength(16);
		amountBox.setValue(amountValue);
		amountBox.setHint(Component.literal("сумма"));
		amountBox.setResponder(value -> amountValue = value);
		addRenderableWidget(amountBox);
		setInitialFocus(amountBox);

		int buttonWidth = (panelWidth - 40) / 2;
		addRenderableWidget(StyledButton.create(Component.literal("Перевести"), ignored -> submit())
				.bounds(panelX + 16, panelY + 88, buttonWidth, FIELD_HEIGHT).build());
		addRenderableWidget(StyledButton.create(Component.literal("Отмена"), ignored -> onClose())
				.bounds(panelX + 24 + buttonWidth, panelY + 88, buttonWidth, FIELD_HEIGHT).build());
	}

	private void submit() {
		if (ClientUi.currentScreen(minecraft) != this) return;
		if (!NearbyPlayerMenuScreen.available()) {
			status = "Доступно только для активного Vanilla-box";
			return;
		}
		ServerCommandService commands = CndlChatPlusClient.SERVER_COMMANDS;
		if (commands == null) {
			status = "Сервис команд недоступен";
			return;
		}
		ServerCommandService.CommandResult result = commands.pay(player, amountBox.getValue());
		if (result.success()) {
			if (ClientUi.currentScreen(minecraft) == this) ClientUi.setScreen(minecraft, null);
		} else {
			status = result.errorMessage();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (amountBox.isFocused()
				&& (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			submit();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT);
		graphics.centeredText(font, title, width / 2, panelY + 12, text());
		graphics.centeredText(font, player, width / 2, panelY + 28, success());
		graphics.text(font, "Сумма", panelX + 16, panelY + 44, textMuted());
		int statusY = panelY + 120;
		for (var line : font.split(Component.literal(status), panelWidth - 32)) {
			graphics.text(font, line, width / 2 - font.width(line) / 2, statusY, danger());
			statusY += font.lineHeight;
		}
	}

	@Override
	public void onClose() {
		if (ClientUi.currentScreen(minecraft) == this) ClientUi.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
