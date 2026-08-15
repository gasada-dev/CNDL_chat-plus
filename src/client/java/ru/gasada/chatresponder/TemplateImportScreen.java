package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TemplateImportScreen extends CompatScreen {
	private final Screen parent;
	private final ServerTemplateRepository repository;
	private final TemplateImportService service;
	private final TemplateSelectionService selection;
	private final ServerTemplateRuntime runtime;
	private final TemplateImportOptions options = TemplateImportOptions.all();
	private List<ServerTemplateInfo> templates = List.of();
	private int sourceIndex;
	private int targetIndex;
	private TemplateImportOptions.ListMode listMode = TemplateImportOptions.ListMode.MERGE;
	private TemplateImportPreview preview;
	private boolean confirmationRequested;
	private String status = "";
	private int statusColor = 0xFF75D98B;

	public TemplateImportScreen(Screen parent) {
		super(Component.literal("Импорт между шаблонами"));
		this.parent = parent;
		this.repository = ConfigManager.templateRepository();
		this.service = new TemplateImportService(repository);
		this.selection = GasadaChatResponderClient.TEMPLATE_SELECTION;
		this.runtime = GasadaChatResponderClient.TEMPLATE_RUNTIME;
	}

	@Override
	protected void init() {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			status = loaded.errorMessage();
			return;
		}
		templates = new ArrayList<>(loaded.value().templates);
		if (templates.size() < 2) {
			status = "Для импорта нужны два шаблона";
			return;
		}
		targetIndex = Math.min(targetIndex == sourceIndex ? sourceIndex + 1 : targetIndex, templates.size() - 1);
		int panelWidth = Math.min(760, width - 24);
		int x = (width - panelWidth) / 2;
		addRenderableWidget(Button.builder(Component.literal("Source: " + templates.get(sourceIndex).name), ignored -> {
			sourceIndex = nextDistinct(sourceIndex, targetIndex);
			invalidatePreview(); rebuild();
		}).bounds(x + 18, 48, panelWidth / 2 - 24, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Target: " + templates.get(targetIndex).name), ignored -> {
			targetIndex = nextDistinct(targetIndex, sourceIndex);
			invalidatePreview(); rebuild();
		}).bounds(x + panelWidth / 2 + 6, 48, panelWidth / 2 - 24, 20).build());

		TemplateImportOptions.Category[] categories = TemplateImportOptions.Category.values();
		for (int index = 0; index < categories.length; index++) {
			TemplateImportOptions.Category category = categories[index];
			int column = index % 2;
			int row = index / 2;
			CycleButton<Boolean> toggle = CycleButton.onOffBuilder(options.selected(category))
					.create(x + 18 + column * (panelWidth / 2), 82 + row * 24,
							panelWidth / 2 - 30, 20, Component.literal(label(category)),
							(button, enabled) -> { options.select(category, enabled); invalidatePreview(); });
			addRenderableWidget(toggle);
		}

		int controlsY = 82 + ((categories.length + 1) / 2) * 24 + 4;
		addRenderableWidget(Button.builder(Component.literal("Списки: " + modeLabel()), ignored -> {
			listMode = switch (listMode) {
				case REPLACE -> TemplateImportOptions.ListMode.MERGE;
				case MERGE -> TemplateImportOptions.ListMode.SKIP;
				case SKIP -> TemplateImportOptions.ListMode.REPLACE;
			};
			for (TemplateImportOptions.Category category : categories) options.listMode(category, listMode);
			invalidatePreview(); rebuild();
		}).bounds(x + 18, controlsY, 170, 20).build());
		CycleButton<Boolean> lastSeen = CycleButton.onOffBuilder(options.overwriteExistingLastSeen())
				.create(x + 196, controlsY, 250, 20, Component.literal("Заменять существующий last seen"),
						(button, enabled) -> { options.overwriteExistingLastSeen(enabled); invalidatePreview(); });
		addRenderableWidget(lastSeen);
		addRenderableWidget(Button.builder(Component.literal("Preview"), ignored -> preview())
				.bounds(x + 18, height - 42, 90, 20).build());
		Button apply = addRenderableWidget(Button.builder(Component.literal(
				confirmationRequested ? "Подтвердить импорт" : "Применить"), ignored -> apply())
				.bounds(x + 114, height - 42, 150, 20).build());
		apply.active = preview != null && preview.valid();
		addRenderableWidget(Button.builder(Component.literal("Назад"), ignored -> onClose())
				.bounds(x + panelWidth - 98, height - 42, 80, 20).build());
	}

	private int nextDistinct(int current, int other) {
		int next = (current + 1) % templates.size();
		return next == other ? (next + 1) % templates.size() : next;
	}

	private void preview() {
		TemplateOperationResult<TemplateImportPreview> result = service.preview(
				templates.get(sourceIndex).id, templates.get(targetIndex).id, options);
		preview = result.value();
		confirmationRequested = false;
		if (!result.success()) setStatus(result.errorMessage(), false);
		else if (!preview.valid()) setStatus(String.join("; ", preview.validationErrors()), false);
		else setStatus("Preview готов: " + preview.summary().size() + " категорий", true);
		rebuild();
	}

	private void apply() {
		if (!confirmationRequested) {
			confirmationRequested = true;
			setStatus("Нажмите подтверждение для записи target", false);
			rebuild();
			return;
		}
		TemplateOperationResult<ServerTemplate> result = service.apply(preview, true);
		if (result.success() && runtime.activeSnapshot()
				.map(snapshot -> snapshot.id().equals(result.value().id)).orElse(false)) {
			selection.select(result.value().id);
		}
		setStatus(result.success() ? "Импорт выполнен" : result.errorMessage(), result.success());
		confirmationRequested = false;
		preview = null;
		rebuild();
	}

	private void invalidatePreview() {
		preview = null;
		confirmationRequested = false;
	}

	private void setStatus(String value, boolean success) {
		status = value;
		statusColor = success ? 0xFF75D98B : 0xFFFF7777;
	}

	private String modeLabel() {
		return switch (listMode) { case REPLACE -> "заменить"; case MERGE -> "объединить"; case SKIP -> "пропустить"; };
	}

	private static String label(TemplateImportOptions.Category category) {
		return switch (category) {
			case REPLY_RULES -> "Правила автоответчика";
			case CHANNELS_AND_MARKERS -> "Каналы и markers";
			case MUTED_WORDS -> "Muted words";
			case MUTED_MINECRAFT_PLAYERS -> "Muted Minecraft";
			case MUTED_DISCORD_USERS -> "Muted Discord";
			case DISCORD_SETTINGS -> "Discord settings";
			case FRIENDS -> "Друзья";
			case LAST_SEEN -> "Last seen";
			case HUD_AND_SOUND -> "HUD и звук";
			case PERIODIC_MESSAGES -> "Рассылки";
			case COMMANDS -> "Команды";
			case PARSER_PATTERNS -> "Parser patterns";
			case PLAYER_INFO -> "Информация об игроке";
		};
	}

	private void rebuild() { clearWidgets(); init(); }

	@Override public void onClose() { ClientUi.setScreen(minecraft, parent); }
	@Override protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE010141D);
	}
	@Override protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.centeredText(font, title, width / 2, 20, 0xFFE8ECF2);
		if (!status.isEmpty()) graphics.centeredText(font, status, width / 2, height - 62, statusColor);
	}
	@Override public boolean isPauseScreen() { return false; }
}
