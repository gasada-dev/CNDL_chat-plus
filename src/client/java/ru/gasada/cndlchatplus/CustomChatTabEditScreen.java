package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CustomChatTabEditScreen extends CompatScreen {
	private final Screen parent;
	private final String editedId;
	private final List<CustomChatTab> existing;
	private final Function<CustomChatTab, Boolean> saveConsumer;
	private String name;
	private final Set<ChatTabSource> sources;
	private String prefix;
	private String status = "";
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	CustomChatTabEditScreen(Screen parent, CustomChatTab source, List<CustomChatTab> existing,
			Function<CustomChatTab, Boolean> saveConsumer) {
		super(Component.literal(source == null ? "Новая вкладка" : "Изменить вкладку"));
		this.parent = parent;
		this.editedId = source == null ? null : source.id;
		this.existing = existing;
		this.saveConsumer = saveConsumer;
		name = source == null ? "" : source.name;
		sources = source == null || source.sources == null || source.sources.isEmpty()
				? EnumSet.noneOf(ChatTabSource.class) : EnumSet.copyOf(source.sources);
		prefix = source == null ? "" : source.outgoingPrefix;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(620, width - 24);
		panelHeight = Math.min(300, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int left = panelX + 18;
		int fieldWidth = panelWidth - 36;
		addField(left, panelY + 54, fieldWidth, ResponderConfig.MAX_CUSTOM_CHAT_TAB_NAME_LENGTH,
				name, "Название", value -> name = value);
		addSourceButtons(left, panelY + 84, fieldWidth);
		addField(left, panelY + 164, fieldWidth, ResponderConfig.MAX_CUSTOM_CHAT_TAB_PREFIX_LENGTH,
				prefix, "Префикс команды, например /gc (необязательно)", value -> prefix = value);

		int buttonsY = panelY + panelHeight - 30;
		addRenderableWidget(StyledButton.create(Component.literal("Сохранить"), ignored -> save())
				.bounds(panelX + panelWidth - 210, buttonsY, 90, FIELD_HEIGHT).build());
		addRenderableWidget(StyledButton.create(Component.literal("Отмена"), ignored -> onClose())
				.bounds(panelX + panelWidth - 110, buttonsY, 90, FIELD_HEIGHT).build());
	}

	private void addSourceButtons(int x, int y, int width) {
		int columns = 3;
		int gap = 8;
		int buttonWidth = (width - gap * (columns - 1)) / columns;
		ChatTabSource[] values = ChatTabSource.values();
		for (int index = 0; index < values.length; index++) {
			ChatTabSource source = values[index];
			int buttonX = x + index % columns * (buttonWidth + gap);
			int buttonY = y + index / columns * 24;
			addRenderableWidget(StyledCycleButton.onOff(sources.contains(source), buttonX, buttonY,
					buttonWidth, FIELD_HEIGHT, Component.literal(source.displayName()), (button, selected) -> {
						if (selected) sources.add(source);
						else sources.remove(source);
					}));
		}
	}

	private void addField(int x, int y, int fieldWidth, int maxLength, String value, String hint,
			java.util.function.Consumer<String> responder) {
		EditBox box = new StyledEditBox(font, x, y, fieldWidth, FIELD_HEIGHT, Component.literal(hint));
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setHint(Component.literal(hint));
		box.setResponder(responder);
		addRenderableWidget(box);
	}

	private void save() {
		String error = ChatTabEditorValidation.error(name, sources, editedId, existing);
		if (error != null) {
			status = error;
			return;
		}
		String id = editedId == null ? UUID.randomUUID().toString() : editedId;
		CustomChatTab saved = new CustomChatTab(id, name.trim(), new ArrayList<>(sources), prefix.trim());
		if (!saveConsumer.apply(saved)) {
			status = "Не удалось сохранить вкладку";
			return;
		}
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
		if (!status.isEmpty()) graphics.centeredText(font, status, width / 2, panelY + 34, ERROR);
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
