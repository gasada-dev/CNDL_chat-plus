package ru.gasada.cndlchatplus;

import net.minecraft.network.chat.Component;

final class MarriageMenuScreen extends CompatScreen {
	private static final int PANEL_WIDTH = 240;
	private static final int PANEL_HEIGHT = 140;

	private final MarriageHudSnapshot.Context context;
	private int panelX;
	private int panelY;
	private String status = "";

	MarriageMenuScreen(MarriageHudSnapshot.Context context) {
		super(Component.literal("Брак"));
		this.context = context;
	}

	@Override
	protected void init() {
		panelX = (width - PANEL_WIDTH) / 2;
		panelY = (height - PANEL_HEIGHT) / 2;
		addAction(MarriageAction.KISS, 40);
		addAction(MarriageAction.HOME, 66);
		addAction(MarriageAction.TP, 92);
	}

	private void addAction(MarriageAction action, int offsetY) {
		addRenderableWidget(StyledButton.create(Component.literal(action.label()), ignored -> execute(action))
				.bounds(panelX + 16, panelY + offsetY, PANEL_WIDTH - 32, UiConstants.FIELD_HEIGHT).build());
	}

	private void execute(MarriageAction action) {
		MarriageHud hud = CndlChatPlusClient.MARRIAGE_HUD;
		if (hud == null) {
			status = "Действия брака недоступны";
			return;
		}
		MarriageHudController.ActionResult result = hud.execute(context, action);
		if (result.success()) onClose();
		else status = result.errorMessage();
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
		graphics.centeredText(font, title, width / 2, panelY + 10, UiConstants.TEXT);
		graphics.centeredText(font, context.partner(), width / 2, panelY + 25, UiConstants.SUCCESS);
		if (!status.isEmpty()) {
			graphics.centeredText(font, status, width / 2, panelY + 119, UiConstants.ERROR);
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
}
