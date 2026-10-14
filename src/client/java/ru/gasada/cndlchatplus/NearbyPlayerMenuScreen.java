package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.ThemeTokens.*;

import net.minecraft.network.chat.Component;

public final class NearbyPlayerMenuScreen extends CompatScreen {
	private static final int PANEL_WIDTH = 260;
	private static final int PANEL_HEIGHT = 224;

	private final String player;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private String status = "";

	public NearbyPlayerMenuScreen(String player) {
		super(Component.literal("Действия с игроком"));
		this.player = player;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(PANEL_WIDTH, width - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - PANEL_HEIGHT) / 2;
		addAction("Добавить в текущий приват", 40, Action.PROTECTION_ADD);
		addAction("Удалить из текущего привата", 64, Action.PROTECTION_REMOVE);
		addAction("Добавить в торговца", 88, Action.TRADER_TRUSTED_ADD);
		addAction("Удалить из торговца", 112, Action.TRADER_TRUSTED_REMOVE);
		addRenderableWidget(StyledButton.create(Component.literal("Перевести деньги"), ignored -> openPayment())
				.bounds(panelX + 16, panelY + 136, panelWidth - 32, FIELD_HEIGHT).build());
		addAction("Выгнать из привата (/pskick)", 160, Action.PS_KICK);
	}

	private void addAction(String label, int offsetY, Action action) {
		addRenderableWidget(StyledButton.create(Component.literal(label), ignored -> execute(action))
				.bounds(panelX + 16, panelY + offsetY, panelWidth - 32, FIELD_HEIGHT).build());
	}

	private ServerCommandService availableCommands() {
		if (ClientUi.currentScreen(minecraft) != this) return null;
		if (!available()) {
			status = "Доступно только для активного Vanilla-box";
			return null;
		}
		ServerCommandService commands = CndlChatPlusClient.SERVER_COMMANDS;
		if (commands == null) {
			status = "Сервис команд недоступен";
		}
		return commands;
	}

	private void openPayment() {
		if (availableCommands() != null) {
			ClientUi.setScreen(minecraft, new PlayerPaymentScreen(this, player));
		}
	}

	private void execute(Action action) {
		ServerCommandService commands = availableCommands();
		if (commands == null) return;
		ServerCommandService.CommandResult result = switch (action) {
			case PROTECTION_ADD -> commands.addToProtection(player);
			case PROTECTION_REMOVE -> commands.removeFromProtection(player);
			case TRADER_TRUSTED_ADD -> commands.addTraderTrusted(player);
			case TRADER_TRUSTED_REMOVE -> commands.removeTraderTrusted(player);
			case PS_KICK -> commands.psKick(player);
		};
		if (result.success()) {
			onClose();
		} else {
			status = result.errorMessage();
		}
	}

	static boolean available() {
		return CndlChatPlusClient.CONNECTION_GATE.active()
				&& CndlChatPlusClient.VANILLA_BOX_RUNTIME != null
				&& CndlChatPlusClient.VANILLA_BOX_RUNTIME.activeSnapshot().isPresent();
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT);
		graphics.centeredText(font, title, width / 2, panelY + 10, text());
		graphics.centeredText(font, player, width / 2, panelY + 25, success());
		int statusY = panelY + 188;
		for (var line : font.split(Component.literal(status), panelWidth - 32)) {
			graphics.text(font, line, width / 2 - font.width(line) / 2, statusY, danger());
			statusY += font.lineHeight;
		}
	}

	@Override
	public void onClose() {
		if (ClientUi.currentScreen(minecraft) == this) ClientUi.setScreen(minecraft, null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum Action {
		PROTECTION_ADD,
		PROTECTION_REMOVE,
		TRADER_TRUSTED_ADD,
		TRADER_TRUSTED_REMOVE,
		PS_KICK
	}
}
