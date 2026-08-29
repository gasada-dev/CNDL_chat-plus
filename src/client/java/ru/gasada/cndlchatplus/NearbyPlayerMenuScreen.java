package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import net.minecraft.network.chat.Component;

public final class NearbyPlayerMenuScreen extends CompatScreen {
	private static final int PANEL_WIDTH = 260;
	private static final int PANEL_HEIGHT = 168;

	private final String player;
	private int panelX;
	private int panelY;
	private String status = "";

	public NearbyPlayerMenuScreen(String player) {
		super(Component.literal("Действия с игроком"));
		this.player = player;
	}

	@Override
	protected void init() {
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = (height - PANEL_HEIGHT) / 2;
		addAction("Добавить в текущий приват", 42, Action.PROTECTION_ADD);
		addAction("Удалить из текущего привата", 68, Action.PROTECTION_REMOVE);
		addAction("Добавить в торговца", 94, Action.TRADER_TRUSTED_ADD);
		addAction("Удалить из торговца", 120, Action.TRADER_TRUSTED_REMOVE);
	}

	private void addAction(String label, int offsetY, Action action) {
		addRenderableWidget(StyledButton.create(Component.literal(label), ignored -> execute(action))
				.bounds(panelX + 16, panelY + offsetY, PANEL_WIDTH - 32, FIELD_HEIGHT).build());
	}

	private void execute(Action action) {
		if (!available()) {
			status = "Доступно только для активного Vanilla-box";
			return;
		}
		ServerCommandService commands = CndlChatPlusClient.SERVER_COMMANDS;
		if (commands == null) {
			status = "Сервис команд недоступен";
			return;
		}
		ServerCommandService.CommandResult result = switch (action) {
			case PROTECTION_ADD -> commands.addToProtection(player);
			case PROTECTION_REMOVE -> commands.removeFromProtection(player);
			case TRADER_TRUSTED_ADD -> commands.addTraderTrusted(player);
			case TRADER_TRUSTED_REMOVE -> commands.removeTraderTrusted(player);
		};
		if (result.success()) {
			onClose();
		} else {
			status = result.errorMessage();
		}
	}

	static boolean available() {
		return CndlChatPlusClient.TEMPLATE_RUNTIME != null
				&& CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(snapshot -> LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID.equals(snapshot.id()))
						.orElse(false);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
		graphics.centeredText(font, title, width / 2, panelY + 10, TEXT);
		graphics.centeredText(font, player, width / 2, panelY + 25, SUCCESS);
		if (!status.isEmpty()) {
			graphics.centeredText(font, status, width / 2, panelY + 146, ERROR);
		}
	}

	@Override
	public void onClose() {
		ClientUi.setScreen(minecraft, null);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum Action {
		PROTECTION_ADD,
		PROTECTION_REMOVE,
		TRADER_TRUSTED_ADD,
		TRADER_TRUSTED_REMOVE
	}
}
