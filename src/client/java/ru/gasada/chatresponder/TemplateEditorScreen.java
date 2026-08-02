package ru.gasada.chatresponder;

import java.util.Arrays;
import java.util.List;

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
	private EditBox nameBox;
	private EditBox patternsBox;
	private String status = "";

	public TemplateEditorScreen(Screen parent, ServerTemplate source, ServerTemplateInfo info,
			ServerTemplateManager manager, ServerTemplateRuntime runtime, TemplateSelectionService selection) {
		super(Component.literal("Редактор шаблона"));
		this.parent = parent;
		this.draft = source.deepCopy(source.id, source.name);
		this.info = info.copy();
		this.manager = manager;
		this.runtime = runtime;
		this.selection = selection;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(620, width - 30);
		int x = (width - panelWidth) / 2;
		nameBox = new EditBox(font, x + 20, 78, panelWidth - 40, 20, Component.literal("Имя"));
		nameBox.setMaxLength(64);
		nameBox.setValue(draft.name);
		addRenderableWidget(nameBox);
		patternsBox = new EditBox(font, x + 20, 126, panelWidth - 40, 20,
				Component.literal("Address patterns"));
		patternsBox.setMaxLength(1024);
		patternsBox.setValue(String.join(", ", info.addressPatterns));
		patternsBox.setHint(Component.literal("play.example.org, *.example.org"));
		addRenderableWidget(patternsBox);
		addRenderableWidget(Button.builder(Component.literal("Сохранить"), ignored -> save())
				.bounds(x + panelWidth - 210, height - 48, 90, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Отмена"), ignored -> onClose())
				.bounds(x + panelWidth - 110, height - 48, 90, 20).build());
	}

	private void save() {
		List<String> patterns = Arrays.stream(patternsBox.getValue().split(","))
				.map(String::trim).filter(value -> !value.isEmpty()).toList();
		TemplateOperationResult<ServerTemplate> saved = manager.saveDraft(draft, nameBox.getValue(), patterns);
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
		graphics.centeredText(font, title, width / 2, 28, 0xFFE8ECF2);
		graphics.text(font, "ID: " + draft.id, 30, 52, 0xFF9DA8B8);
		graphics.text(font, "Имя", 30, 66, 0xFF9DA8B8);
		graphics.text(font, "Адреса и wildcard поддомена (через запятую)", 30, 114, 0xFF9DA8B8);
		if (!status.isEmpty()) {
			graphics.centeredText(font, status, width / 2, height - 70, 0xFFFF7777);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
