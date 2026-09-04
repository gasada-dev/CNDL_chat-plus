package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChatAlertEditScreen extends CompatScreen {
	private final Screen parent;
	private final ChatAlertRule draft;
	private final Consumer<ChatAlertRule> saveConsumer;
	private String patternValue;
	private String status = "";
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	public ChatAlertEditScreen(Screen parent, ChatAlertRule source, Consumer<ChatAlertRule> saveConsumer) {
		super(Component.literal(source == null ? "Новое alert-правило" : "Изменить alert-правило"));
		this.parent = parent;
		this.draft = source == null ? new ChatAlertRule() : source.copy();
		this.saveConsumer = saveConsumer;
		this.patternValue = draft.pattern;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(620, width - 24);
		panelHeight = Math.min(300, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int left = panelX + 18;
		int gap = 12;
		int columnWidth = (panelWidth - 48 - gap) / 2;
		int right = left + columnWidth + gap;
		addField(left, panelY + 52, panelWidth - 36, ResponderConfig.MAX_CHAT_ALERT_PATTERN_LENGTH,
				patternValue, "Шаблон", value -> patternValue = value);
		addRenderableWidget(StyledCycleButton.of(value -> Component.literal(value.displayName()), draft.matchType,
				List.of(ChatAlertMatchType.values()), left, panelY + 88, columnWidth, FIELD_HEIGHT,
				Component.literal("Тип"), (button, value) -> draft.matchType = value));
		addRenderableWidget(StyledCycleButton.of(value -> Component.literal(value.displayName()), draft.channel,
				List.of(ChatAlertChannel.values()), right, panelY + 88, columnWidth, FIELD_HEIGHT,
				Component.literal("Канал"), (button, value) -> draft.channel = value));
		addRenderableWidget(StyledCycleButton.onOff(draft.hudEnabled, left, panelY + 124, columnWidth,
				FIELD_HEIGHT, Component.literal("HUD"), (button, value) -> draft.hudEnabled = value));
		addRenderableWidget(StyledCycleButton.onOff(draft.soundEnabled, right, panelY + 124, columnWidth,
				FIELD_HEIGHT, Component.literal("Звук"), (button, value) -> draft.soundEnabled = value));

		int buttonsY = panelY + panelHeight - 30;
		addRenderableWidget(StyledButton.create(Component.literal("Сохранить"), ignored -> save())
				.bounds(panelX + panelWidth - 210, buttonsY, 90, FIELD_HEIGHT).build());
		addRenderableWidget(StyledButton.create(Component.literal("Отмена"), ignored -> onClose())
				.bounds(panelX + panelWidth - 110, buttonsY, 90, FIELD_HEIGHT).build());
	}

	private void addField(int x, int y, int fieldWidth, int maxLength, String value, String hint,
			Consumer<String> responder) {
		EditBox box = new StyledEditBox(font, x, y, fieldWidth, FIELD_HEIGHT, Component.literal(hint));
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setHint(Component.literal(hint));
		box.setResponder(responder);
		addRenderableWidget(box);
	}

	private void save() {
		String error = new ChatAlertRuleCompiler().validationError(draft.matchType, patternValue);
		if (error != null) {
			status = error;
			return;
		}
		draft.pattern = patternValue;
		draft.cooldownSeconds = 0;
		saveConsumer.accept(draft);
		onClose();
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 14);
		if (!status.isEmpty()) graphics.centeredText(font, status, width / 2, panelY + 30, ERROR);
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
