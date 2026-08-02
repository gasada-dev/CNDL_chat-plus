package ru.gasada.chatresponder;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TemplateEditorScreen extends Screen {
	private final Screen parent;
	private final ServerTemplate draft;
	private final ServerTemplateInfo info;
	private final ServerTemplateManager manager;
	private final ServerTemplateRuntime runtime;
	private final TemplateSelectionService selection;
	private EditorPage page = EditorPage.GENERAL;
	private String nameValue;
	private String patternsValue;
	private String status = "";
	private int panelX;
	private int panelWidth;

	public TemplateEditorScreen(Screen parent, ServerTemplate source, ServerTemplateInfo info,
			ServerTemplateManager manager, ServerTemplateRuntime runtime, TemplateSelectionService selection) {
		super(Component.literal("Редактор шаблона"));
		this.parent = parent;
		this.draft = source.deepCopy(source.id, source.name);
		this.info = info.copy();
		this.manager = manager;
		this.runtime = runtime;
		this.selection = selection;
		this.nameValue = draft.name;
		this.patternsValue = String.join(", ", info.addressPatterns);
	}

	@Override
	protected void init() {
		panelWidth = Math.min(760, width - 24);
		panelX = (width - panelWidth) / 2;
		int tabWidth = panelWidth / 3;
		for (int index = 0; index < EditorPage.values().length; index++) {
			EditorPage target = EditorPage.values()[index];
			Button tab = addRenderableWidget(Button.builder(Component.literal(target.title), ignored -> {
				page = target;
				status = "";
				rebuild();
			}).bounds(panelX + index * tabWidth, 43,
					index == 2 ? panelWidth - tabWidth * 2 : tabWidth, 20).build());
			tab.active = page != target;
		}

		switch (page) {
			case GENERAL -> initGeneral();
			case COMMANDS -> initCommands();
			case DISCORD -> initDiscord();
		}
		addRenderableWidget(Button.builder(Component.literal("Сохранить"), ignored -> save())
				.bounds(panelX + panelWidth - 210, height - 32, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Отмена"), ignored -> onClose())
				.bounds(panelX + panelWidth - 110, height - 32, 90, 20).build());
	}

	private void initGeneral() {
		addField(panelX + 20, 84, panelWidth - 40, 64, nameValue, "Имя шаблона", value -> nameValue = value);
		addField(panelX + 20, 130, panelWidth - 40, 1024, patternsValue,
				"play.example.org, *.example.org", value -> patternsValue = value);
	}

	private void initCommands() {
		int gap = 16;
		int columnWidth = (panelWidth - 56 - gap) / 2;
		int left = panelX + 20;
		int right = left + columnWidth + gap;
		addCommandField(left, 78, columnWidth, draft.commands.ignorePlayer,
				"ignoreplayer {player}", value -> draft.commands.ignorePlayer = value);
		addCommandField(left, 114, columnWidth, draft.commands.privateMessage,
				"w {player} {message}", value -> draft.commands.privateMessage = value);
		addCommandField(left, 150, columnWidth, draft.commands.pay,
				"pay {player} {amount}", value -> draft.commands.pay = value);
		addCommandField(left, 186, columnWidth, draft.privateReplyCommand,
				"/r", value -> draft.privateReplyCommand = value);
		addCommandField(right, 78, columnWidth, draft.commands.lookupFriend,
				"clan lookup {player}", value -> draft.commands.lookupFriend = value);
		addCommandField(right, 114, columnWidth, draft.commands.call,
				"call {player}", value -> draft.commands.call = value);
		addCommandField(right, 150, columnWidth, draft.commands.mail,
				"mail send {player} {message}", value -> draft.commands.mail = value);
	}

	private void initDiscord() {
		addField(panelX + 20, 84, panelWidth - 40, ParserPatternValidator.MAX_PATTERN_LENGTH,
				draft.parsers.discordMarkerPattern, "Regex маркера: Discord, DS, Bridge...",
				value -> draft.parsers.discordMarkerPattern = value);
		addField(panelX + 20, 130, panelWidth - 40, ParserPatternValidator.MAX_PATTERN_LENGTH,
				draft.parsers.discordNamePattern, "Regex имени автора Discord",
				value -> draft.parsers.discordNamePattern = value);
	}

	private void addCommandField(int x, int y, int width, String value, String hint,
			Consumer<String> responder) {
		addField(x, y, width, CommandTemplateValidator.MAX_LENGTH, value, hint, responder);
	}

	private void addField(int x, int y, int width, int maxLength, String value, String hint,
			Consumer<String> responder) {
		EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
		box.setMaxLength(maxLength);
		box.setValue(value == null ? "" : value);
		box.setHint(Component.literal(hint));
		box.setResponder(responder);
		addRenderableWidget(box);
	}

	private void save() {
		List<String> validationErrors = TemplateSettingsValidator.validate(draft);
		if (!validationErrors.isEmpty()) {
			status = validationErrors.getFirst();
			return;
		}
		List<String> patterns = Arrays.stream(patternsValue.split(","))
				.map(String::trim).filter(value -> !value.isEmpty()).toList();
		TemplateOperationResult<ServerTemplate> saved = manager.saveDraft(draft, nameValue, patterns);
		if (!saved.success()) {
			status = saved.errorMessage();
			return;
		}
		if (runtime.activeSnapshot().map(snapshot -> snapshot.id().equals(draft.id)).orElse(false)) {
			TemplateOperationResult<ServerTemplate> selected = selection.select(draft.id);
			if (!selected.success()) {
				status = selected.errorMessage();
				return;
			}
		}
		onClose();
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE010141D);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.centeredText(font, title, width / 2, 15, 0xFFE8ECF2);
		graphics.text(font, "ID: " + draft.id, panelX + 4, 30, 0xFF9DA8B8);
		switch (page) {
			case GENERAL -> {
				label(graphics, "Имя", panelX + 20, 72);
				label(graphics, "Адреса и wildcard поддомена (через запятую)", panelX + 20, 118);
			}
			case COMMANDS -> drawCommandLabels(graphics);
			case DISCORD -> {
				label(graphics, "Как распознать маркер Discord в строке чата", panelX + 20, 72);
				label(graphics, "Как извлечь имя Discord-пользователя", panelX + 20, 118);
			}
		}
		if (!status.isEmpty()) graphics.centeredText(font, status, width / 2, height - 48, 0xFFFF7777);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawCommandLabels(GuiGraphicsExtractor graphics) {
		int gap = 16;
		int columnWidth = (panelWidth - 56 - gap) / 2;
		int left = panelX + 20;
		int right = left + columnWidth + gap;
		label(graphics, "Чёрный список — {player}", left, 66);
		label(graphics, "Личное сообщение — {player}, {message}", left, 102);
		label(graphics, "Перевод — {player}, {amount}", left, 138);
		label(graphics, "Ответ автоответчика в ЛС (с начальным /)", left, 174);
		label(graphics, "Поиск друга — {player}", right, 66);
		label(graphics, "Телепорт — {player}", right, 102);
		label(graphics, "Почта — {player}, {message}", right, 138);
	}

	private void label(GuiGraphicsExtractor graphics, String text, int x, int y) {
		graphics.text(font, text, x, y, 0xFF9DA8B8);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum EditorPage {
		GENERAL("Основное"), COMMANDS("Команды"), DISCORD("Discord");

		private final String title;
		EditorPage(String title) { this.title = title; }
	}
}
