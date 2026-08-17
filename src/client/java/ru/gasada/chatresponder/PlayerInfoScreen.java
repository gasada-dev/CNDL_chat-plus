package ru.gasada.chatresponder;

import static ru.gasada.chatresponder.UiConstants.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PlayerInfoScreen extends CompatScreen {
	private static final int TEXT_COLOR = TEXT;
	private static final int MUTED_COLOR = MUTED;
	private static final int ERROR_COLOR = ERROR;
	private static final int SUCCESS_COLOR = SUCCESS;
	private static final int BUILDINGS_PER_PAGE = 6;

	private final Screen parent;
	private final PlayerSuggestionProvider suggestions = new PlayerSuggestionProvider();
	private final List<Button> suggestionButtons = new ArrayList<>();
	private EditBox playerBox;
	private String playerValue = "";
	private String selectedPlayer;
	private PlayerInfoProfile profile;
	private PlayerLookupData lookupData;
	private String status = "Выберите игрока и нажмите «Обновить»";
	private int statusColor = MUTED_COLOR;
	private boolean loading;
	private int requestSerial;
	private int buildingPage;
	private long observedGeneration;
	private Object observedConnection;
	private boolean contextObserved;

	public PlayerInfoScreen(Screen parent) {
		super(Component.literal("Информация об игроке"));
		this.parent = parent;
	}

	public PlayerInfoScreen(Screen parent, String player) {
		this(parent);
		playerValue = player;
	}

	@Override
	protected void init() {
		suggestionButtons.clear();
		int panelWidth = Math.min(760, width - 30);
		int panelX = (width - panelWidth) / 2;
		playerBox = new StyledEditBox(font, panelX + 18, 54, 220, 20, Component.literal("Ник игрока"));
		playerBox.setMaxLength(PlayerNameValidator.MAX_LENGTH);
		playerBox.setHint(Component.literal("Ник игрока"));
		playerBox.setValue(playerValue);
		playerBox.setResponder(value -> {
			playerValue = value;
			refreshSuggestions();
		});
		addRenderableWidget(playerBox);

		Button refresh = addRenderableWidget(StyledButton.create(Component.literal("Обновить"), ignored -> refresh())
				.bounds(panelX + 246, 54, 90, 20)
				.tooltip(Tooltip.create(Component.literal("Загрузить свежие данные профиля"))).build());
		refresh.active = !loading;
		addRenderableWidget(StyledButton.create(Component.literal("Назад"), ignored -> onClose())
				.bounds(panelX + panelWidth - 108, 54, 90, 20).build());

		int suggestionWidth = Math.max(70, Math.min(120, (panelWidth - 36) / 4));
		for (int index = 0; index < 4; index++) {
			Button button = addRenderableWidget(StyledButton.create(Component.empty(), clicked -> select(clicked.getMessage().getString()))
					.bounds(panelX + 18 + index * (suggestionWidth + 4), 80, suggestionWidth, 18).build());
			button.visible = false;
			suggestionButtons.add(button);
		}
		refreshSuggestions();

		if (profile != null && profile.buildings().size() > BUILDINGS_PER_PAGE) {
			int pages = (profile.buildings().size() - 1) / BUILDINGS_PER_PAGE;
			Button previous = addRenderableWidget(StyledButton.create(Component.literal("<"), ignored -> {
				buildingPage--;
				rebuildContents();
			}).bounds(panelX + panelWidth - 94, height - 44, 30, 20).build());
			previous.active = buildingPage > 0;
			Button next = addRenderableWidget(StyledButton.create(Component.literal(">"), ignored -> {
				buildingPage++;
				rebuildContents();
			}).bounds(panelX + panelWidth - 56, height - 44, 30, 20).build());
			next.active = buildingPage < pages;
		}
	}

	private void select(String player) {
		playerValue = player;
		selectedPlayer = player;
		playerBox.setValue(player);
		profile = GasadaChatResponderClient.PLAYER_INFO == null ? null
				: GasadaChatResponderClient.PLAYER_INFO.cached(player).orElse(null);
		lookupData = null;
		buildingPage = 0;
		status = profile == null ? "Игрок выбран; нажмите «Обновить»" : "Показаны данные из кэша сеанса";
		statusColor = profile == null ? MUTED_COLOR : SUCCESS_COLOR;
		refreshSuggestions();
	}

	private void refresh() {
		String player = playerBox.getValue().trim();
		PlayerNameValidator.ValidationResult validation = PlayerNameValidator.validate(player);
		if (!validation.valid()) {
			status = validation.errorMessage();
			statusColor = ERROR_COLOR;
			return;
		}
		if (GasadaChatResponderClient.PLAYER_INFO == null) {
			status = "Сервис профилей не инициализирован";
			statusColor = ERROR_COLOR;
			return;
		}
		selectedPlayer = player;
		profile = null;
		lookupData = null;
		loading = true;
		status = "Загрузка…";
		statusColor = MUTED_COLOR;
		int serial = ++requestSerial;
		rebuildContents();
		GasadaChatResponderClient.PLAYER_INFO.refresh(player).whenComplete((result, error) ->
				minecraft.execute(() -> {
					if (serial != requestSerial) return;
					loading = false;
					if (error != null || result == null) {
						status = "Не удалось загрузить профиль";
						statusColor = ERROR_COLOR;
					} else if (result.success()) {
						profile = result.profile();
						lookupData = result.lookupData();
						status = result.message().isBlank() ? "Профиль обновлён" : result.message();
						statusColor = result.fallback() ? MUTED_COLOR : SUCCESS_COLOR;
					} else {
						status = result.message();
						statusColor = ERROR_COLOR;
					}
					buildingPage = 0;
					rebuildContents();
				}));
	}

	private void refreshSuggestions() {
		if (playerBox == null) return;
		List<String> names = suggestions.suggest(minecraft, playerBox.getValue(), suggestionButtons.size());
		for (int index = 0; index < suggestionButtons.size(); index++) {
			Button button = suggestionButtons.get(index);
			button.visible = index < names.size();
			button.active = button.visible;
			if (button.visible) button.setMessage(Component.literal(names.get(index)));
		}
	}

	private boolean isOnline() {
		return selectedPlayer != null && minecraft != null && minecraft.getConnection() != null
				&& minecraft.getConnection().getPlayerInfoIgnoreCase(selectedPlayer) != null;
	}

	@Override
	public void tick() {
		long generation = GasadaChatResponderClient.TEMPLATE_RUNTIME == null ? -1L
				: GasadaChatResponderClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::generation).orElse(-1L);
		Object connection = minecraft == null ? null : minecraft.getConnection();
		if (!contextObserved) {
			contextObserved = true;
			observedGeneration = generation;
			observedConnection = connection;
		} else if (observedGeneration != generation || observedConnection != connection) {
			observedGeneration = generation;
			observedConnection = connection;
			requestSerial++;
			profile = null;
			lookupData = null;
			selectedPlayer = null;
			buildingPage = 0;
			status = "Подключение или шаблон изменены; выберите игрока заново";
			statusColor = MUTED_COLOR;
			loading = false;
			rebuildContents();
			return;
		}
		refreshSuggestions();
	}

	@Override
	public void onClose() {
		requestSerial++;
		ClientUi.setScreen(minecraft, parent);
	}

	@Override
	protected void repositionElements() {
		rebuildContents();
	}

	private void rebuildContents() {
		clearWidgets();
		init();
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		int panelWidth = Math.min(760, width - 30);
		int panelX = (width - panelWidth) / 2;
		ScreenChrome.drawPanel(graphics, panelX, 30, panelWidth, height - 50);
		ScreenChrome.drawHeader(graphics, font, title, width / 2, 36);
		graphics.text(font, status, panelX + 350, 60, statusColor);

		int y = 110;
		if (selectedPlayer != null) {
			graphics.text(font, "Игрок: " + selectedPlayer, panelX + 18, y, TEXT_COLOR);
			graphics.text(font, isOnline() ? "Онлайн сейчас" : "Не найден в списке онлайн",
					panelX + 220, y, isOnline() ? SUCCESS_COLOR : MUTED_COLOR);
			y += 18;
		}
		if (profile != null) {
			y = drawProfile(graphics, panelX + 18, y);
		} else if (lookupData != null) {
			drawLookupData(graphics, panelX + 18, y);
		}
		CreditRenderer.draw(graphics, font, panelX + 4, height - 14, MUTED_COLOR);
	}

	private int drawProfile(CompatGraphics graphics, int x, int y) {
		y = line(graphics, x, y, "Регистрация", profile.registrationDate());
		y = line(graphics, x, y, "Последний вход", profile.lastLogin());
		if (profile.clan() != null) {
			String clan = join(profile.clan().tag(), profile.clan().name());
			y = line(graphics, x, y, "Клан", clan);
			y = line(graphics, x, y, "Лидер клана", profile.clan().leaderName());
			if (profile.clan().playerIsLeader()) {
				y = line(graphics, x, y, "Роль в клане", "лидер");
			}
		}
		if (profile.marriage() != null) {
			y = line(graphics, x, y, "Супруг", profile.marriage().partner());
			y = line(graphics, x, y, "Дата брака", profile.marriage().date());
			y = line(graphics, x, y, "Фамилия", profile.marriage().surname());
		}
		y = line(graphics, x, y, "О себе", profile.about());
		y = line(graphics, x, y, "Город", profile.city());
		y = line(graphics, x, y, "Telegram", profile.telegram());
		y = line(graphics, x, y, "VK", profile.vk());
		y = line(graphics, x, y, "Сайт", profile.website());
		if (!profile.buildings().isEmpty()) {
			graphics.text(font, "Постройки:", x, y, TEXT_COLOR);
			y += 14;
			int from = buildingPage * BUILDINGS_PER_PAGE;
			int to = Math.min(profile.buildings().size(), from + BUILDINGS_PER_PAGE);
			for (PlayerInfoProfile.Building building : profile.buildings().subList(from, to)) {
				String rating = building.rating() == null ? "" : " — рейтинг " + building.rating();
				graphics.text(font, "• " + building.title() + rating, x + 10, y, MUTED_COLOR);
				y += 14;
			}
		}
		return y;
	}

	private int drawLookupData(CompatGraphics graphics, int x, int y) {
		graphics.text(font, "Данные сервера:", x, y, TEXT_COLOR);
		y += 16;
		if (lookupData.lastSeen() != null) {
			y = line(graphics, x, y, "Последнее посещение", lookupData.lastSeen());
		}
		for (java.util.Map.Entry<String, String> entry : lookupData.fields().entrySet()) {
			y = line(graphics, x, y, entry.getKey(), entry.getValue());
		}
		return y;
	}

	private int line(CompatGraphics graphics, int x, int y, String label, String value) {
		if (value == null || value.isBlank()) return y;
		graphics.text(font, label + ": " + value, x, y, TEXT_COLOR);
		return y + 14;
	}

	private static String join(String first, String second) {
		if (first == null) return second;
		if (second == null) return first;
		return first + " — " + second;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
