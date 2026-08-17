package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TemplateEditorScreen extends CompatScreen {
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
		int tabWidth = panelWidth / EditorPage.values().length;
		for (int index = 0; index < EditorPage.values().length; index++) {
			EditorPage target = EditorPage.values()[index];
			Button tab = addRenderableWidget(StyledButton.create(Component.literal(target.title), ignored -> {
				page = target;
				status = "";
				rebuild();
			}).bounds(panelX + index * tabWidth, 43,
					index == EditorPage.values().length - 1
							? panelWidth - tabWidth * index : tabWidth, 20).build());
			tab.active = page != target;
		}

		switch (page) {
			case GENERAL -> initGeneral();
			case COMMANDS -> initCommands();
			case DISCORD -> initDiscord();
			case PLAYER_INFO -> initPlayerInfo();
		}
		addRenderableWidget(StyledButton.create(Component.literal("Сохранить"), ignored -> save())
				.bounds(panelX + panelWidth - 210, height - 32, 90, 20).build());
		addRenderableWidget(StyledButton.create(Component.literal("Отмена"), ignored -> onClose())
				.bounds(panelX + panelWidth - 110, height - 32, 90, 20).build());
	}

	private void initGeneral() {
		addField(panelX + 20, 84, panelWidth - 40, 64, nameValue, "Имя шаблона", value -> nameValue = value);
		addField(panelX + 20, 130, panelWidth - 40, 1024, patternsValue,
				"play.example.org, *.example.org", value -> patternsValue = value);
		addRenderableWidget(StyledCycleButton.of(TemplateEditorScreen::providerTitle,
				draft.playerInfo.provider == null ? PlayerInfoProvider.NONE : draft.playerInfo.provider,
				List.of(PlayerInfoProvider.values()), panelX + 20, 176,
				Math.min(260, panelWidth - 40), 20, Component.empty(),
				(button, value) -> {
					draft.playerInfo.provider = value;
					draft.playerInfo.providerConfigured = true;
				}));
	}

	private static Component providerTitle(PlayerInfoProvider provider) {
		return Component.literal(provider == PlayerInfoProvider.VANILLA_GAME_PUBLIC_API
				? "Публичный API VanillaGame" : "Только серверный lookup");
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
		if (isVanillaGame()) {
			addCommandField(right, 186, columnWidth, draft.commands.marriageList,
					"marry list {page}", value -> {
						draft.commands.marriageList = value;
						draft.playerInfo.marriageLookupConfigured = true;
					});
		}
		addCommandField(left, 222, columnWidth, draft.commands.acceptTeleport,
				"tpaccept", value -> draft.commands.acceptTeleport = value);
		addField(right, 222, columnWidth, ParserPatternValidator.MAX_PATTERN_LENGTH,
				draft.parsers.teleportRequestPattern, "Regex запроса ТП, ник в group 1", value -> {
					draft.parsers.teleportRequestPattern = value;
					draft.parsers.teleportRequestConfigured = true;
				});
	}

	private void initDiscord() {
		addField(panelX + 20, 84, panelWidth - 40, ParserPatternValidator.MAX_PATTERN_LENGTH,
				draft.parsers.discordMarkerPattern, "Regex маркера: Discord, DS, Bridge...",
				value -> draft.parsers.discordMarkerPattern = value);
		addField(panelX + 20, 130, panelWidth - 40, ParserPatternValidator.MAX_PATTERN_LENGTH,
				draft.parsers.discordNamePattern, "Regex имени автора Discord",
				value -> draft.parsers.discordNamePattern = value);
	}

	private void initPlayerInfo() {
		if (draft.parsers.playerInfoPatterns == null) {
			draft.parsers.playerInfoPatterns = new LinkedHashMap<>();
		}
		int gap = 16;
		int columnWidth = (panelWidth - 56 - gap) / 2;
		int left = panelX + 20;
		int right = left + columnWidth + gap;
		for (int index = 0; index < ParserSettings.PLAYER_INFO_FIELDS.size(); index++) {
			String field = ParserSettings.PLAYER_INFO_FIELDS.get(index);
			int x = index % 2 == 0 ? left : right;
			int y = 78 + (index / 2) * 38;
			addField(x, y, columnWidth, ParserPatternValidator.MAX_PATTERN_LENGTH,
					draft.parsers.playerInfoPatterns.getOrDefault(field, ""),
					"Regex, capture group 1", value -> {
						draft.parsers.playerInfoPatterns.put(field, value);
						draft.parsers.playerInfoPatternsConfigured = true;
					});
		}
		if (isVanillaGame()) {
			addField(left, 278, columnWidth, ParserPatternValidator.MAX_PATTERN_LENGTH,
					draft.parsers.marriageEntryPattern, "Regex: два ника в groups 1 и 2", value -> {
						draft.parsers.marriageEntryPattern = value;
						draft.playerInfo.marriageLookupConfigured = true;
					});
			addField(right, 278, columnWidth, ParserPatternValidator.MAX_PATTERN_LENGTH,
					draft.parsers.marriagePagePattern, "Regex: page/max в groups 1 и 2", value -> {
						draft.parsers.marriagePagePattern = value;
						draft.playerInfo.marriageLookupConfigured = true;
					});
			addField(left, 326, columnWidth, ParserPatternValidator.MAX_PATTERN_LENGTH,
					draft.parsers.marriageEmptyPattern, "Regex: список браков пуст", value -> {
						draft.parsers.marriageEmptyPattern = value;
						draft.playerInfo.marriageLookupConfigured = true;
					});
		}
	}

	private void addCommandField(int x, int y, int width, String value, String hint,
			Consumer<String> responder) {
		addField(x, y, width, CommandTemplateValidator.MAX_LENGTH, value, hint, responder);
	}

	private void addField(int x, int y, int width, int maxLength, String value, String hint,
			Consumer<String> responder) {
		EditBox box = new StyledEditBox(font, x, y, width, 20, Component.literal(hint));
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
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawHeader(graphics, font, title, width / 2, 15);
		graphics.text(font, "ID: " + draft.id, panelX + 4, 30, MUTED);
		int tabCount = EditorPage.values().length;
		int tabWidth = panelWidth / tabCount;
		int activeX = panelX + page.ordinal() * tabWidth;
		int activeWidth = page.ordinal() == tabCount - 1 ? panelWidth - tabWidth * page.ordinal() : tabWidth;
		graphics.fill(activeX + 3, 63, activeX + activeWidth - 3, 65, ACCENT);
		switch (page) {
			case GENERAL -> {
				label(graphics, "Имя", panelX + 20, 72);
				label(graphics, "Адреса и wildcard поддомена (через запятую)", panelX + 20, 118);
				label(graphics, "Источник информации об игроке", panelX + 20, 164);
			}
			case COMMANDS -> drawCommandLabels(graphics);
			case DISCORD -> {
				label(graphics, "Как распознать маркер Discord в строке чата", panelX + 20, 72);
				label(graphics, "Как извлечь имя Discord-пользователя", panelX + 20, 118);
			}
			case PLAYER_INFO -> drawPlayerInfoLabels(graphics);
		}
		if (!status.isEmpty()) graphics.centeredText(font, status, width / 2, height - 48, ERROR);
	}

	private void drawCommandLabels(CompatGraphics graphics) {
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
		if (isVanillaGame()) label(graphics, "Список браков — {page}", right, 174);
	}

	private void drawPlayerInfoLabels(CompatGraphics graphics) {
		int gap = 16;
		int columnWidth = (panelWidth - 56 - gap) / 2;
		int left = panelX + 20;
		int right = left + columnWidth + gap;
		for (int index = 0; index < ParserSettings.PLAYER_INFO_FIELDS.size(); index++) {
			int x = index % 2 == 0 ? left : right;
			int y = 66 + (index / 2) * 38;
			label(graphics, ParserSettings.PLAYER_INFO_FIELDS.get(index) + " — capture group 1", x, y);
		}
		if (isVanillaGame()) {
			label(graphics, "Строка брака — ники в groups 1 и 2", left, 266);
			label(graphics, "Страница списка — current/max", right, 266);
			label(graphics, "Пустой список браков", left, 314);
		}
	}

	private boolean isVanillaGame() {
		return "vanilla-game".equals(draft.id);
	}

	private void label(CompatGraphics graphics, String text, int x, int y) {
		graphics.text(font, text, x, y, MUTED);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum EditorPage {
		GENERAL("Основное"), COMMANDS("Команды"), DISCORD("Discord"), PLAYER_INFO("Инфо игрока");

		private final String title;
		EditorPage(String title) { this.title = title; }
	}
}
