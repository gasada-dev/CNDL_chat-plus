package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PeriodicMessageScreen extends CompatScreen {
	private static final int TEXT_COLOR = 0xFFE8ECF2;
	private static final int MUTED_COLOR = 0xFF9DA8B8;

	private final Screen parent;
	private final ResponderConfig config;
	private final List<PeriodicMessageConfig> drafts = new ArrayList<>();
	private final List<EditBox> minuteBoxes = new ArrayList<>();
	private String statusText = "";
	private int statusColor = 0xFF75D98B;

	public PeriodicMessageScreen(Screen parent, ResponderConfig config) {
		super(Component.literal("Периодические сообщения"));
		this.parent = parent;
		this.config = config;
		for (PeriodicMessageConfig message : config.periodicMessages) {
			drafts.add(message.copy());
		}
		if (drafts.isEmpty()) {
			drafts.add(new PeriodicMessageConfig());
		}
	}

	@Override
	protected void init() {
		minuteBoxes.clear();
		int panelWidth = Math.min(700, width - 30);
		int panelX = (width - panelWidth) / 2;
		int y = 80;

		for (int index = 0; index < drafts.size(); index++) {
			addMessageRow(panelX, panelWidth, y, drafts.get(index), index);
			y += 32;
		}

		Button add = addRenderableWidget(Button.builder(Component.literal("+ Рассылка"), ignored -> {
			drafts.add(new PeriodicMessageConfig());
			rebuildContents();
		}).bounds(panelX + 18, height - 48, 100, 20)
				.tooltip(help("Добавить ещё одну рассылку, максимум три")).build());
		add.active = drafts.size() < PeriodicMessageConfig.MAX_PERIODIC_MESSAGES;

		addRenderableWidget(Button.builder(Component.literal("Сохранить"), ignored -> save())
				.bounds(panelX + panelWidth - 208, height - 48, 90, 20)
				.tooltip(help("Сохранить тексты, интервалы и состояния рассылок")).build());
		addRenderableWidget(Button.builder(Component.literal("Назад"), ignored -> onClose())
				.bounds(panelX + panelWidth - 108, height - 48, 90, 20)
				.tooltip(help("Вернуться к правилам автоответа")).build());
	}

	private void addMessageRow(int panelX, int panelWidth, int y, PeriodicMessageConfig draft, int index) {
		int x = panelX + 18;
		int available = panelWidth - 36;
		int enabledWidth = 108;
		int minutesWidth = 62;
		int deleteWidth = 22;
		int messageWidth = available - enabledWidth - minutesWidth - deleteWidth - 12;

		EditBox message = new EditBox(font, x, y, messageWidth, 20, Component.literal("Сообщение"));
		message.setMaxLength(256);
		message.setValue(draft.message);
		message.setHint(Component.literal("текст или !текст"));
		message.setResponder(value -> draft.message = value);
		addRenderableWidget(message);
		x += messageWidth + 4;

		EditBox minutes = new EditBox(font, x, y, minutesWidth, 20, Component.literal("Минуты"));
		minutes.setMaxLength(7);
		minutes.setValue(Integer.toString(draft.intervalMinutes));
		minutes.setHint(Component.literal("5"));
		minuteBoxes.add(addRenderableWidget(minutes));
		x += minutesWidth + 4;

		CycleButton<Boolean> enabled = CycleButton.builder(
				value -> Component.literal(value ? "Включено" : "Выключено"), draft.enabled)
				.withValues(false, true)
				.displayOnlyValue()
				.create(x, y, enabledWidth, 20, Component.literal("Рассылка"),
						(button, value) -> draft.enabled = value);
		addRenderableWidget(enabled);
		enabled.setTooltip(help("Включить или выключить эту периодическую рассылку"));
		x += enabledWidth + 4;

		addRenderableWidget(Button.builder(Component.literal("×"), ignored -> {
			drafts.remove(index);
			if (drafts.isEmpty()) {
				drafts.add(new PeriodicMessageConfig());
			}
			rebuildContents();
		}).bounds(x, y, deleteWidth, 20)
				.tooltip(help("Удалить эту рассылку")).build());
	}

	private void save() {
		for (int index = 0; index < drafts.size(); index++) {
			PeriodicMessageConfig draft = drafts.get(index);
			int minutes;
			try {
				minutes = Integer.parseInt(minuteBoxes.get(index).getValue().trim());
			} catch (NumberFormatException exception) {
				setStatus("Рассылка " + (index + 1) + ": укажите целое число минут", 0xFFFF7777);
				return;
			}

			if (minutes < 1 || minutes > 525_600) {
				setStatus("Рассылка " + (index + 1) + ": интервал от 1 до 525600 минут", 0xFFFF7777);
				return;
			}
			if (draft.enabled && draft.message.isBlank()) {
				setStatus("Рассылка " + (index + 1) + ": введите сообщение", 0xFFFF7777);
				return;
			}
			draft.intervalMinutes = minutes;
			draft.message = draft.message.trim();
		}

		config.periodicMessages = drafts.stream().map(PeriodicMessageConfig::copy)
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		boolean saved = ConfigManager.save(config);
		setStatus(saved ? "Настройки сохранены" : "Ошибка сохранения",
				saved ? 0xFF75D98B : 0xFFFF7777);
	}

	private void rebuildContents() {
		clearWidgets();
		init();
	}

	private void setStatus(String text, int color) {
		statusText = text;
		statusColor = color;
	}

	private static Tooltip help(String text) {
		return Tooltip.create(Component.literal(text));
	}

	@Override
	protected void repositionElements() {
		rebuildContents();
	}

	@Override
	public void onClose() {
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE010141D);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		int panelWidth = Math.min(700, width - 30);
		int x = (width - panelWidth) / 2;
		graphics.fill(x - 2, 35, x + panelWidth + 2, height - 20, 0xFF536178);
		graphics.fill(x, 37, x + panelWidth, height - 22, 0xD9242B38);
		graphics.centeredText(font, title, width / 2, 48, TEXT_COLOR);
		graphics.text(font, "Сообщение", x + 18, 67, MUTED_COLOR);
		int available = panelWidth - 36;
		int messageWidth = available - 108 - 62 - 22 - 12;
		graphics.text(font, "Интервал, мин", x + 22 + messageWidth, 67, MUTED_COLOR);
		if (statusText.isEmpty()) {
			graphics.text(font, "До трёх независимых рассылок; отсчёт начинается после сохранения.",
					x + 18, height - 66, MUTED_COLOR);
		} else {
			graphics.centeredText(font, statusText, width / 2, height - 68, statusColor);
		}
		CreditRenderer.draw(graphics, font, x + 4, height - 14, MUTED_COLOR);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
