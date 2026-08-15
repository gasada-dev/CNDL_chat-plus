package ru.gasada.chatresponder;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TemplatesScreen extends CompatScreen {
	private final Screen parent;
	private final ServerTemplateRepository repository;
	private final ServerTemplateManager manager;
	private final ServerTemplateRuntime runtime;
	private final TemplateSelectionService selection;
	private RootConfig root = new RootConfig();
	private String selectedId;
	private String pendingDeleteId;
	private EditBox idBox;
	private EditBox nameBox;
	private String status = "";
	private int statusColor = 0xFF75D98B;

	public TemplatesScreen(Screen parent) {
		super(Component.literal("Серверные шаблоны"));
		this.parent = parent;
		this.repository = ConfigManager.templateRepository();
		this.manager = new ServerTemplateManager(repository);
		this.runtime = GasadaChatResponderClient.TEMPLATE_RUNTIME;
		this.selection = GasadaChatResponderClient.TEMPLATE_SELECTION;
	}

	@Override
	protected void init() {
		TemplateOperationResult<RootConfig> loaded = repository.loadRoot();
		if (!loaded.success()) {
			setStatus(loaded.errorMessage(), false);
			return;
		}
		root = loaded.value();
		String activeId = activeId();
		if (selectedId == null || root.templates.stream().noneMatch(info -> selectedId.equals(info.id))) {
			selectedId = activeId != null ? activeId
					: root.templates.isEmpty() ? null : root.templates.getFirst().id;
		}

		int panelWidth = Math.min(780, width - 24);
		int x = (width - panelWidth) / 2;
		int listWidth = Math.min(330, panelWidth / 2);
		int y = 54;
		for (ServerTemplateInfo info : root.templates.stream().limit(9).toList()) {
			String prefix = info.id.equals(activeId) ? "● " : info.id.equals(selectedId) ? "▶ " : "";
			Button button = addRenderableWidget(Button.builder(
					Component.literal(prefix + info.name + "  [" + info.id + "]"), ignored -> {
						selectedId = info.id;
						pendingDeleteId = null;
						rebuild();
					}).bounds(x + 16, y, listWidth - 24, 20).build());
			button.active = !info.id.equals(selectedId);
			y += 23;
		}

		int actionX = x + listWidth + 14;
		int actionWidth = panelWidth - listWidth - 30;
		addAction(actionX, 54, actionWidth, "Редактировать", this::editSelected);
		addAction(actionX, 78, actionWidth, "Выбрать временно", this::selectTemporary);
		addAction(actionX, 102, actionWidth, "Сделать шаблоном по умолчанию", this::setDefault);
		addAction(actionX, 126, actionWidth, "Привязать текущий адрес", this::bindCurrentAddress);
		addAction(actionX, 150, actionWidth, "Удалить", this::deleteSelected);
		int importButtonWidth = (actionWidth - 4) / 2;
		addRenderableWidget(Button.builder(Component.literal("Импорт между"), ignored ->
				ClientUi.setScreen(minecraft, new TemplateImportScreen(this)))
				.bounds(actionX, 174, importButtonWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Загрузить шаблоны из папки"), ignored ->
				loadFromFolder()).bounds(actionX + importButtonWidth + 4, 174,
						actionWidth - importButtonWidth - 4, 20)
				.tooltip(help(ConfigManager.templateImportDirectory().toString())).build());

		int createY = height - 91;
		idBox = new EditBox(font, x + 16, createY, 150, 20, Component.literal("ID шаблона"));
		idBox.setMaxLength(64);
		idBox.setHint(Component.literal("my-server"));
		addRenderableWidget(idBox);
		nameBox = new EditBox(font, x + 172, createY, 190, 20, Component.literal("Имя шаблона"));
		nameBox.setMaxLength(64);
		nameBox.setHint(Component.literal("Мой сервер"));
		addRenderableWidget(nameBox);
		addRenderableWidget(Button.builder(Component.literal("Пустой"), ignored -> create(null))
				.bounds(x + 368, createY, 72, 20).tooltip(help("Создать пустой шаблон")).build());
		addRenderableWidget(Button.builder(Component.literal("Vanilla-box"), ignored ->
				create(LegacyConfigToVanillaBoxMigration.VANILLA_BOX_ID))
				.bounds(x + 444, createY, 100, 20).tooltip(help("Создать копию Vanilla-box")).build());
		addRenderableWidget(Button.builder(Component.literal("Копия выбранного"), ignored -> create(selectedId))
				.bounds(x + 548, createY, Math.max(110, panelWidth - 564), 20).build());
		addRenderableWidget(Button.builder(Component.literal("Назад"), ignored -> onClose())
				.bounds(x + panelWidth - 96, height - 36, 80, 20).build());
	}

	private void addAction(int x, int y, int width, String label, Runnable action) {
		Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run())
				.bounds(x, y, width, 20).build());
		button.active = selectedId != null;
	}

	private void create(String sourceId) {
		String id = idBox.getValue().trim();
		String name = nameBox.getValue().trim();
		TemplateOperationResult<ServerTemplate> result = sourceId == null
				? manager.createEmpty(id, name) : manager.copy(sourceId, id, name);
		if (result.success()) {
			selectedId = result.value().id;
			setStatus("Шаблон создан", true);
			rebuild();
		} else {
			setStatus(result.errorMessage(), false);
		}
	}

	private void editSelected() {
		TemplateOperationResult<ServerTemplate> loaded = repository.loadTemplate(selectedId);
		if (!loaded.success()) {
			setStatus(loaded.errorMessage(), false);
			return;
		}
		ServerTemplateInfo info = root.templates.stream().filter(value -> selectedId.equals(value.id))
				.findFirst().orElse(new ServerTemplateInfo(selectedId, loaded.value().name));
		ClientUi.setScreen(minecraft, new TemplateEditorScreen(this, loaded.value(), info, manager,
				runtime, selection));
	}

	private void selectTemporary() {
		TemplateOperationResult<ServerTemplate> result = selection.select(selectedId);
		setStatus(result.success() ? "Активный шаблон изменён" : result.errorMessage(), result.success());
		rebuild();
	}

	private void setDefault() {
		TemplateOperationResult<Void> result = manager.setDefault(selectedId);
		setStatus(result.success() ? "Шаблон выбран по умолчанию" : result.errorMessage(), result.success());
		rebuild();
	}

	private void bindCurrentAddress() {
		String address = selection.currentAddress();
		if (address == null) {
			setStatus("Текущий адрес сервера недоступен", false);
			return;
		}
		TemplateOperationResult<Void> result = manager.bindAddress(address, selectedId);
		setStatus(result.success() ? "Адрес привязан постоянно" : result.errorMessage(), result.success());
	}

	private void loadFromFolder() {
		TemplateCatalogService catalog = GasadaChatResponderClient.TEMPLATE_CATALOG != null
				? GasadaChatResponderClient.TEMPLATE_CATALOG
				: new TemplateCatalogService(repository, ConfigManager.templateImportDirectory());
		TemplateCatalogService.ImportSummary result = catalog.importUserTemplates();
		if (result.success()) {
			setStatus("Загружено: " + result.installed() + ", пропущено: " + result.skipped(), true);
		} else {
			setStatus("Ошибки импорта: " + String.join("; ", result.errors()), false);
		}
		rebuild();
	}

	private void deleteSelected() {
		if (!selectedId.equals(pendingDeleteId)) {
			pendingDeleteId = selectedId;
			setStatus("Нажмите «Удалить» ещё раз для подтверждения", false);
			return;
		}
		TemplateOperationResult<Void> result = manager.delete(selectedId, activeId());
		if (result.success()) {
			selectedId = null;
			pendingDeleteId = null;
		}
		setStatus(result.success() ? "Шаблон удалён" : result.errorMessage(), result.success());
		rebuild();
	}

	private String activeId() {
		return runtime == null ? null : runtime.activeSnapshot().map(ActiveTemplateSnapshot::id).orElse(null);
	}

	private void setStatus(String value, boolean success) {
		status = value;
		statusColor = success ? 0xFF75D98B : 0xFFFF7777;
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
		graphics.fill(0, 0, width, height, 0xE010141D);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.centeredText(font, title, width / 2, 20, 0xFFE8ECF2);
		String active = runtime == null ? "нет" : runtime.activeSnapshot()
				.map(ActiveTemplateSnapshot::name).orElse("нет");
		graphics.text(font, "Активный: " + active, 20, 36, 0xFF75D98B);
		graphics.text(font, "Default: " + (root.defaultTemplateId == null ? "нет" : root.defaultTemplateId),
				width - 190, 36, 0xFF9DA8B8);
		if (!status.isEmpty()) {
			graphics.centeredText(font, status, width / 2, height - 57, statusColor);
		}
	}

	private static Tooltip help(String text) {
		return Tooltip.create(Component.literal(text));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
