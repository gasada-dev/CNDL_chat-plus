package ru.gasada.cndlchatplus;

import java.util.List;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

final class ThemeScreen extends Screen {
	private final Screen parent;
	private OwoUIAdapter<FlowLayout> adapter;

	ThemeScreen(Screen parent) {
		super(Component.literal("Темы"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		super.init();
		adapter = OwoUIAdapter.create(this, UIContainers::verticalFlow);
		FlowLayout root = adapter.rootComponent;
		root.surface(Surface.flat(ThemeTokens.background()));
		root.padding(Insets.of(ThemeTokens.safeSpacingLarge()));
		root.gap(ThemeTokens.safeSpacingSmall());
		root.child(UIComponents.label(title).color(OwoThemeBridge.color(ThemeTokens.text())));
		root.child(UIComponents.label(Component.literal("Найдено: " + ThemeManager.available().size() + " • " + ThemeManager.themesDirectory()))
				.color(OwoThemeBridge.color(ThemeTokens.textMuted())));

		FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		list.gap(ThemeTokens.safeSpacingSmall());
		String activeId = ThemeManager.current().meta().id();
		List<UiTheme.Meta> themes = ThemeManager.available();
		if (themes.isEmpty()) themes = List.of(UiTheme.defaults().meta());
		for (UiTheme.Meta theme : themes) list.child(themeEntry(theme, activeId));
		root.child(UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(100), list));

		FlowLayout controls = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		controls.gap(ThemeTokens.safeSpacingSmall());
		controls.child(UIComponents.button(Component.literal("Обновить список"), ignored -> {
			ThemeManager.reload();
			ClientUi.openThemeScreen(minecraft, parent);
		}));
		controls.child(UIComponents.button(Component.literal("Открыть папку"), ignored -> {
			try {
				Util.getPlatform().openFile(ThemeManager.themesDirectory().toFile());
			} catch (RuntimeException error) {
				CndlChatPlusClient.LOGGER.warn("Не удалось открыть папку тем");
			}
		}));
		controls.child(UIComponents.button(Component.literal("Назад"), ignored -> onClose()));
		root.child(controls);
		adapter.inflateAndMount();
	}

	private FlowLayout themeEntry(UiTheme.Meta theme, String activeId) {
		boolean active = theme.id().equals(activeId);
		ButtonComponent entry = UIComponents.button(
				Component.literal(theme.name() + " — " + theme.author() + (active ? "  ✔" : "")),
				ignored -> apply(theme.id()));
		entry.horizontalSizing(Sizing.fill(100));
		entry.active(!active);
		FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		panel.surface(active ? OwoThemeBridge.accentPanel() : OwoThemeBridge.panel());
		panel.padding(OwoThemeBridge.padding());
		panel.child(entry);
		return panel;
	}

	private void apply(String id) {
		if (ThemeManager.setTheme(id)) {
			ChatAccess.chat(minecraft).rescaleChat();
			ClientUi.openThemeScreen(minecraft, parent);
		}
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
