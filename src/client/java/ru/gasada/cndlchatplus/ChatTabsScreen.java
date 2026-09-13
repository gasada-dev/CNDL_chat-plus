package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChatTabsScreen extends CompatScreen {
	private final Screen parent;
	private final ResponderConfig config;
	private final ScreenStatus status = new ScreenStatus();
	private int page;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	ChatTabsScreen(Screen parent, ResponderConfig config) {
		super(Component.literal("Настройка вкладок"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(700, width - 24);
		panelHeight = Math.min(360, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		int left = panelX + 16;
		int usable = panelWidth - 32;
		int pageSize = Math.max(2, (panelHeight - 112) / 25);
		List<Entry> entries = entries();
		page = Pagination.clampPage(page, entries.size(), pageSize);
		int start = page * pageSize;
		for (int row = 0; row < pageSize && start + row < entries.size(); row++) {
			Entry entry = entries.get(start + row);
			int y = panelY + 48 + row * 25;
			if (entry.builtIn() != null) addBuiltIn(entry.builtIn(), left, y, usable);
			else addCustom(entry.custom(), left, y, usable);
		}

		int controlsY = panelY + panelHeight - 30;
		Button previous = addRenderableWidget(StyledButton.create(Component.literal("<"), ignored -> {
			page--;
			rebuild();
		}).bounds(left, controlsY, 28, FIELD_HEIGHT).build());
		previous.active = page > 0;
		Button next = addRenderableWidget(StyledButton.create(Component.literal(">"), ignored -> {
			page++;
			rebuild();
		}).bounds(left + 32, controlsY, 28, FIELD_HEIGHT).build());
		next.active = page < Pagination.maxPage(entries.size(), pageSize);
		Button add = addRenderableWidget(StyledButton.create(Component.literal("Добавить вкладку"), ignored -> addTab())
				.bounds(left + 68, controlsY, Math.min(130, usable - 168), FIELD_HEIGHT).build());
		add.active = config.customChatTabs.size() < ResponderConfig.MAX_CUSTOM_CHAT_TABS;
		addRenderableWidget(StyledButton.create(Component.literal("Готово"), ignored -> onClose())
				.bounds(panelX + panelWidth - 96, controlsY, 80, FIELD_HEIGHT).build());
	}

	private void addBuiltIn(ChatTab tab, int x, int y, int rowWidth) {
		boolean visible = !config.hiddenBuiltInTabs.contains(tab.name());
		StyledCycleButton<Boolean> toggle = StyledCycleButton.onOff(visible, x, y, rowWidth, FIELD_HEIGHT,
				Component.literal("Встроенная: " + tab.displayName()), (button, value) -> {
					List<String> hidden = new ArrayList<>(config.hiddenBuiltInTabs);
					if (value) hidden.remove(tab.name());
					else if (!hidden.contains(tab.name())) hidden.add(tab.name());
					persist(copyTabs(config.customChatTabs), hidden, "Видимость вкладки сохранена");
					rebuild();
				});
		toggle.setTooltip(Tooltip.create(Component.literal("Показывать встроенную вкладку над чатом")));
		addRenderableWidget(toggle);
	}

	private void addCustom(CustomChatTab tab, int x, int y, int rowWidth) {
		int deleteWidth = 28;
		String suffix = tab.outgoingPrefix.isBlank() ? "" : " | " + tab.outgoingPrefix;
		String sources = tab.sources.stream().map(ChatTabSource::displayName)
				.collect(java.util.stream.Collectors.joining(", "));
		addRenderableWidget(StyledButton.create(Component.literal(tab.name + " | " + sources + suffix),
				ignored -> editTab(tab)).bounds(x, y, rowWidth - deleteWidth - 4, FIELD_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Изменить пользовательскую вкладку"))).build());
		addRenderableWidget(StyledButton.create(Component.literal("×"), ignored -> deleteTab(tab))
				.bounds(x + rowWidth - deleteWidth, y, deleteWidth, FIELD_HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Удалить пользовательскую вкладку"))).build());
	}

	private List<Entry> entries() {
		List<Entry> entries = new ArrayList<>();
		for (ChatTab tab : ChatTab.values()) {
			if (tab != ChatTab.ALL) entries.add(new Entry(tab, null));
		}
		for (CustomChatTab tab : config.customChatTabs) entries.add(new Entry(null, tab));
		return entries;
	}

	private void addTab() {
		if (config.customChatTabs.size() >= ResponderConfig.MAX_CUSTOM_CHAT_TABS) {
			status.set("Достигнут лимит пользовательских вкладок", ERROR);
			return;
		}
		ClientUi.setScreen(minecraft, new CustomChatTabEditScreen(this, null,
				copyTabs(config.customChatTabs), this::saveTab));
	}

	private void editTab(CustomChatTab tab) {
		ClientUi.setScreen(minecraft, new CustomChatTabEditScreen(this, tab,
				copyTabs(config.customChatTabs), this::saveTab));
	}

	private boolean saveTab(CustomChatTab tab) {
		List<CustomChatTab> draft = copyTabs(config.customChatTabs);
		int index = indexOf(draft, tab.id);
		if (index < 0) draft.add(tab);
		else draft.set(index, tab);
		return persist(draft, new ArrayList<>(config.hiddenBuiltInTabs), "Вкладка сохранена");
	}

	private void deleteTab(CustomChatTab tab) {
		List<CustomChatTab> draft = copyTabs(config.customChatTabs);
		draft.removeIf(candidate -> candidate.id.equals(tab.id));
		if (persist(draft, new ArrayList<>(config.hiddenBuiltInTabs), "Вкладка удалена")) rebuild();
	}

	private boolean persist(List<CustomChatTab> tabs, List<String> hidden, String successMessage) {
		List<CustomChatTab> previousTabs = config.customChatTabs;
		List<String> previousHidden = config.hiddenBuiltInTabs;
		config.customChatTabs = tabs;
		config.hiddenBuiltInTabs = hidden;
		if (!ConfigManager.saveGlobalSettings(config)) {
			config.customChatTabs = previousTabs;
			config.hiddenBuiltInTabs = previousHidden;
			status.set("Не удалось сохранить настройки вкладок", ERROR);
			return false;
		}
		if (CndlChatPlusClient.CHAT_TABS != null) {
			CndlChatPlusClient.CHAT_TABS.reloadConfig(config);
			CndlChatPlusClient.CHAT_TABS.refresh(minecraft);
		}
		status.set(successMessage, SUCCESS);
		return true;
	}

	private static int indexOf(List<CustomChatTab> tabs, String id) {
		for (int index = 0; index < tabs.size(); index++) {
			if (tabs.get(index).id.equals(id)) return index;
		}
		return -1;
	}

	private static List<CustomChatTab> copyTabs(List<CustomChatTab> tabs) {
		return tabs.stream().map(CustomChatTab::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, panelY, panelWidth, panelHeight);
		ScreenChrome.drawHeader(graphics, font, title, width / 2, panelY + 14);
		if (!status.empty()) graphics.centeredText(font, status.text(), width / 2,
				panelY + panelHeight - 46, status.color());
	}

	@Override
	public void onClose() {
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record Entry(ChatTab builtIn, CustomChatTab custom) { }
}
