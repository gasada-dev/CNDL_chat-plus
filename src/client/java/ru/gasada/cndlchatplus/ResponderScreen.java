package ru.gasada.cndlchatplus;

import static ru.gasada.cndlchatplus.UiConstants.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class ResponderScreen extends CompatScreen {
	private final ResponderConfig config;
	private final BlacklistTabController blacklistController;
	private final FriendsTabController friendsController;
	private final PlayerSuggestionProvider suggestionProvider = new PlayerSuggestionProvider();
	private final ScreenStatus status = new ScreenStatus();
	private final List<Button> suggestionButtons = new ArrayList<>();
	private final List<Button> friendSuggestionButtons = new ArrayList<>();
	private Tab tab = Tab.BLACKLIST;
	private int panelX;
	private int panelWidth;
	private EditBox nicknameBox;
	private EditBox wordBox;
	private EditBox friendNameBox;
	private EditBox friendMessageBox;
	private EditBox friendMailBox;
	private EditBox friendAmountBox;
	private String nicknameValue = "";
	private String wordValue = "";
	private String friendNameValue = "";
	private String friendMessageValue = "";
	private String friendMailValue = "";
	private String friendAmountValue = "";
	private String selectedFriend;
	private int friendPage;
	private int friendOnlineRefreshTicks;
	private int friendLastSeenHash;
	private boolean friendLookupsQueued;
	private Set<String> onlineFriends = Set.of();
	private BlacklistMode blacklistMode = BlacklistMode.NICKS;

	public ResponderScreen(ResponderConfig config) {
		super(Component.literal("CNDL_chat+"));
		this.config = config;
		this.blacklistController = new BlacklistTabController(config);
		this.friendsController = new FriendsTabController(config);
	}

	@Override
	protected void init() {
		panelWidth = Math.min(820, width - 20);
		panelX = (width - panelWidth) / 2;
		initTemplateSelector();

		int half = panelWidth / 2;
		int infoX = panelX + half;
		int titleX = infoX - font.width(title) - 4;
		addRenderableWidget(StyledButton.create(Component.literal("Подсказка"), ignored ->
				ClientUi.setScreen(minecraft, new HelpScreen(this)))
				.bounds(titleX - 80, 2, 74, 18)
				.tooltip(help("Показать возможности и управление CNDL_chat+")).build());
		addRenderableWidget(StyledButton.create(Component.literal("Информация об игроке"), ignored ->
				ClientUi.setScreen(minecraft, new PlayerInfoScreen(this)))
				.bounds(panelX + half, 2, panelWidth - half, 18)
				.tooltip(help("Открыть профиль игрока активного сервера")).build());
		addTabButton(Tab.BLACKLIST, panelX, 27, half);
		addTabButton(Tab.FRIENDS, panelX + half, 27, panelWidth - half);

		switch (tab) {
			case BLACKLIST -> initBlacklistTab();
			case FRIENDS -> initFriendsTab();
		}
	}

	private void addTabButton(Tab target, int x, int y, int buttonWidth) {
		Button button = addRenderableWidget(StyledButton.create(Component.literal(target.title), ignored -> {
			tab = target;
			status.clear();
			rebuildContents();
		}).bounds(x, y, buttonWidth, 20).build());
		button.active = tab != target;
		button.setTooltip(help(target.help));
	}

	private void initTemplateSelector() {
		int infoX = panelX + panelWidth / 2;
		int settingsX = Math.min(panelX + 198, infoX - 30);
		TemplateOperationResult<RootConfig> loaded = ConfigManager.templateRepository().loadRoot();
		String activeId = CndlChatPlusClient.TEMPLATE_RUNTIME == null ? null
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::id).orElse(null);
		if (loaded.success() && !loaded.value().templates.isEmpty()) {
			List<String> ids = loaded.value().templates.stream().map(info -> info.id).toList();
			String initial = activeId != null && ids.contains(activeId) ? activeId : ids.getFirst();
			StyledCycleButton<String> selector = StyledCycleButton.of(
					id -> Component.literal(templateName(loaded.value(), id)), initial, ids,
					panelX + 6, 2, Math.max(60, settingsX - panelX - 10), 18, Component.empty(),
					(button, id) -> selectTemplate(id));
			addRenderableWidget(selector);
			selector.setTooltip(help("Активный серверный шаблон"));
		}
		addRenderableWidget(StyledButton.create(Component.literal("⚙"), ignored ->
				ClientUi.setScreen(minecraft, new TemplatesScreen(this)))
				.bounds(settingsX, 2, 28, 18)
				.tooltip(help("Настройки серверных шаблонов")).build());
	}

	private String templateName(RootConfig root, String id) {
		return root.templates.stream().filter(info -> id.equals(info.id))
				.map(info -> info.name).findFirst().orElse(id);
	}

	private void selectTemplate(String id) {
		String current = CndlChatPlusClient.TEMPLATE_RUNTIME == null ? null
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::id).orElse(null);
		if (id.equals(current)) return;
		if (!saveCurrentTab()) {
			setStatus("Не удалось сохранить текущий шаблон", ERROR);
			rebuildContents();
			return;
		}
		TemplateOperationResult<ServerTemplate> selected = CndlChatPlusClient.TEMPLATE_SELECTION.select(id);
		if (!selected.success()) {
			setStatus(selected.errorMessage(), ERROR);
			rebuildContents();
			return;
		}
		selectedFriend = null;
		friendLookupsQueued = false;
		setStatus("Активный шаблон: " + selected.value().name, SUCCESS);
		rebuildContents();
	}

	private void initBlacklistTab() {
		int modeWidth = 100;
		int modesX = panelX + (panelWidth - modeWidth * 2) / 2;
		Button nickMode = addRenderableWidget(StyledButton.create(Component.literal("Ники"), ignored -> {
			blacklistMode = BlacklistMode.NICKS;
			status.clear();
			rebuildContents();
		}).bounds(modesX, 55, modeWidth, FIELD_HEIGHT)
				.tooltip(help("Открыть мут обычных и Discord-пользователей")).build());
		nickMode.active = blacklistMode != BlacklistMode.NICKS;

		Button wordMode = addRenderableWidget(StyledButton.create(Component.literal("Слова"), ignored -> {
			blacklistMode = BlacklistMode.WORDS;
			status.clear();
			rebuildContents();
		}).bounds(modesX + modeWidth, 55, modeWidth, FIELD_HEIGHT)
				.tooltip(help("Открыть чёрный список слов и фраз")).build());
		wordMode.active = blacklistMode != BlacklistMode.WORDS;

		if (blacklistMode == BlacklistMode.NICKS) {
			initNicknameBlacklist();
		} else {
			initWordBlacklist();
		}

		int bottomButtonWidth = Math.min(180, panelWidth - 36);
		StyledCycleButton<Boolean> discordChat = StyledCycleButton.of(
				value -> Component.literal(value ? "Включён" : "Выключен"), config.discordChatEnabled,
				List.of(false, true), panelX + 18, height - 38, bottomButtonWidth, FIELD_HEIGHT,
				Component.literal("Чат Discord"),
				(button, value) -> config.discordChatEnabled = value);
		addRenderableWidget(discordChat);
		discordChat.setTooltip(help("Показывать или полностью скрывать все сообщения из Discord"));

	}

	private void initNicknameBlacklist() {
		int x = panelX + 18;
		int muteWidth = 72;
		int discordMuteWidth = 112;
		int fieldWidth = panelWidth - 36 - muteWidth - discordMuteWidth - 8;

		nicknameBox = new StyledEditBox(font, x, 84, fieldWidth, FIELD_HEIGHT, Component.literal("Ник"));
		nicknameBox.setMaxLength(32);
		nicknameBox.setValue(nicknameValue);
		nicknameBox.setHint(Component.literal("ник игрока"));
		addRenderableWidget(nicknameBox);
		x += fieldWidth + 4;

		addRenderableWidget(StyledButton.create(Component.literal("Мут"), ignored -> addMutedPlayer())
				.bounds(x, 84, muteWidth, FIELD_HEIGHT)
				.tooltip(help("Отправить серверную команду /ignoreplayer для этого ника")).build());
		x += muteWidth + 4;

		addRenderableWidget(StyledButton.create(Component.literal("Мут Discord"), ignored -> addDiscordMutedPlayer())
				.bounds(x, 84, discordMuteWidth, FIELD_HEIGHT)
				.tooltip(help("Скрывать сообщения этого пользователя из Discord")).build());

		int columnGap = 10;
		int columnWidth = (panelWidth - 46) / 2;
		int listY = 118;
		int visibleRows = Math.max(2, Math.min(5, (height - 180) / 22));
		for (int index = 0; index < visibleRows; index++) {
			Button suggestion = StyledButton.create(Component.empty(), button -> {
				nicknameValue = button.getMessage().getString();
				nicknameBox.setValue(nicknameValue);
			}).bounds(panelX + 18, listY + index * 22, columnWidth, FIELD_HEIGHT)
					.tooltip(help("Подставить этот ник в поле ввода")).build();
			suggestion.visible = false;
			suggestionButtons.add(addRenderableWidget(suggestion));
		}

		int mutedX = panelX + 18 + columnWidth + columnGap;
		int mutedCount = Math.min(visibleRows, config.discordMutedPlayers.size());
		for (int index = 0; index < mutedCount; index++) {
			String name = config.discordMutedPlayers.get(index);
			addRenderableWidget(StyledButton.create(Component.literal(name + " (discord)  ×"), ignored -> {
				blacklistController.removeDiscord(name);
				rebuildContents();
			}).bounds(mutedX, listY + index * 22, columnWidth, FIELD_HEIGHT)
					.tooltip(help("Нажмите, чтобы снять локальный Discord-мут")).build());
		}

		nicknameBox.setResponder(value -> {
			nicknameValue = value;
			refreshSuggestions(value);
		});
		refreshSuggestions(nicknameValue);
	}

	private void initWordBlacklist() {
		int x = panelX + 18;
		int addWidth = 105;
		wordBox = new StyledEditBox(font, x, 84, panelWidth - 40 - addWidth, FIELD_HEIGHT,
				Component.literal("Скрываемое слово"));
		wordBox.setMaxLength(64);
		wordBox.setValue(wordValue);
		wordBox.setHint(Component.literal("слово, фраза или маска*"));
		wordBox.setResponder(value -> wordValue = value);
		addRenderableWidget(wordBox);

		addRenderableWidget(StyledButton.create(Component.literal("Мут"), ignored -> addMutedWord())
				.bounds(panelX + panelWidth - 18 - addWidth, 84, addWidth, FIELD_HEIGHT)
				.tooltip(help("Скрывать сообщения с введённым словом или фразой")).build());

		int visibleRows = Math.max(2, Math.min(5, (height - 180) / 22));
		int count = Math.min(visibleRows, config.mutedWords.size());
		for (int index = 0; index < count; index++) {
			String word = config.mutedWords.get(index);
			addRenderableWidget(StyledButton.create(Component.literal("\"" + word + "\"  ×"), ignored -> {
				blacklistController.removeWord(word);
				rebuildContents();
			}).bounds(panelX + 18, 130 + index * 22, Math.min(400, panelWidth - 36), FIELD_HEIGHT)
					.tooltip(help("Нажмите, чтобы удалить слово из чёрного списка")).build());
		}
	}

	private void initFriendsTab() {
		onlineFriends = currentOnlineFriends();
		friendLastSeenHash = activeFriendLastSeen().hashCode();
		if (!friendLookupsQueued && CndlChatPlusClient.FRIEND_LOOKUP != null) {
			CndlChatPlusClient.FRIEND_LOOKUP.queueActiveFriends();
			friendLookupsQueued = true;
		}
		int columnGap = 10;
		int columnWidth = (panelWidth - 46) / 2;
		int leftX = panelX + 18;
		int rightX = leftX + columnWidth + columnGap;
		int addWidth = 82;

		friendNameBox = new StyledEditBox(font, leftX, 60, columnWidth - addWidth - 4, FIELD_HEIGHT,
				Component.literal("Ник друга"));
		friendNameBox.setMaxLength(16);
		friendNameBox.setValue(friendNameValue);
		friendNameBox.setHint(Component.literal("ник будущего друга"));
		friendNameBox.setResponder(value -> {
			friendNameValue = value;
			refreshFriendSuggestions(value);
		});
		addRenderableWidget(friendNameBox);

		addRenderableWidget(StyledButton.create(Component.literal("Добавить"), ignored -> addFriend())
				.bounds(leftX + columnWidth - addWidth, 60, addWidth, FIELD_HEIGHT)
				.tooltip(help("Добавить введённый ник в сохранённый список друзей")).build());

		for (int index = 0; index < 2; index++) {
			Button suggestion = StyledButton.create(Component.empty(), button -> {
				friendNameValue = button.getMessage().getString();
				friendNameBox.setValue(friendNameValue);
			}).bounds(leftX, 86 + index * 22, columnWidth, FIELD_HEIGHT)
					.tooltip(help("Подставить этот онлайн-ник в поле друга")).build();
			suggestion.visible = false;
			friendSuggestionButtons.add(addRenderableWidget(suggestion));
		}

		int visibleRows = Math.max(2, Math.min(8, (height - 218) / 22));
		int maxPage = maxFriendPage(visibleRows);
		friendPage = Math.max(0, Math.min(friendPage, maxPage));
		int start = friendPage * visibleRows;
		List<String> activeFriends = FriendListSorter.sort(activeFriends(), onlineFriends,
				activeFriendLastSeen(), LocalDateTime.now());
		int count = Math.min(visibleRows, activeFriends.size() - start);
		for (int offset = 0; offset < count; offset++) {
			String friend = activeFriends.get(start + offset);
			boolean online = onlineFriends.contains(friend.toLowerCase(Locale.ROOT));
			int y = 151 + offset * 22;
			String selection = friend.equalsIgnoreCase(selectedFriend == null ? "" : selectedFriend) ? "▶ " : "";
			String lastSeen = lastSeenFor(friend);
			String label = selection + friend + (online ? " — онлайн" : " — был: " + lastSeen);
			Component labelComponent = Component.literal(label).withColor(online ? 0x55FF55 : 0xA0A0A0);
			addRenderableWidget(StyledButton.create(labelComponent, ignored -> {
				selectedFriend = friend;
				setStatus("Выбран друг: " + friend, SUCCESS);
				rebuildContents();
			}).bounds(leftX, y, columnWidth - 26, FIELD_HEIGHT)
					.tooltip(help(online ? "Друг сейчас онлайн"
							: "Последний раз был в сети: " + lastSeen)).build());
			addRenderableWidget(StyledButton.create(Component.literal("×"), ignored -> removeFriend(friend))
					.bounds(leftX + columnWidth - 22, y, 22, FIELD_HEIGHT)
					.tooltip(help("Удалить этого игрока из списка друзей")).build());
		}

		Button previous = addRenderableWidget(StyledButton.create(Component.literal("<"), ignored -> {
			friendPage--;
			rebuildContents();
		}).bounds(leftX, height - 38, 30, FIELD_HEIGHT)
				.tooltip(help("Предыдущая страница списка друзей")).build());
		previous.active = friendPage > 0;
		Button next = addRenderableWidget(StyledButton.create(Component.literal(">"), ignored -> {
			friendPage++;
			rebuildContents();
		}).bounds(leftX + 36, height - 38, 30, FIELD_HEIGHT)
				.tooltip(help("Следующая страница списка друзей")).build());
		next.active = friendPage < maxPage;
		int actionWidth = 84;
		friendMessageBox = new StyledEditBox(font, rightX, 78, columnWidth - actionWidth - 4, FIELD_HEIGHT,
				Component.literal("Личное сообщение"));
		friendMessageBox.setMaxLength(220);
		friendMessageBox.setValue(friendMessageValue);
		friendMessageBox.setHint(Component.literal("текст личного сообщения"));
		friendMessageBox.setResponder(value -> friendMessageValue = value);
		addRenderableWidget(friendMessageBox);
		addRenderableWidget(StyledButton.create(Component.literal("Отправить ЛС"), ignored -> sendPrivateToFriend())
				.bounds(rightX + columnWidth - actionWidth, 78, actionWidth, FIELD_HEIGHT)
				.tooltip(help(commandHelp("Личное сообщение", ActiveTemplateSnapshot.CommandSnapshot::privateMessage)))
				.build());

		friendMailBox = new StyledEditBox(font, rightX, 103, columnWidth - actionWidth - 4, FIELD_HEIGHT,
				Component.literal("Сообщение на почту"));
		friendMailBox.setMaxLength(220);
		friendMailBox.setValue(friendMailValue);
		friendMailBox.setHint(Component.literal("текст сообщения на почту"));
		friendMailBox.setResponder(value -> friendMailValue = value);
		addRenderableWidget(friendMailBox);
		addRenderableWidget(StyledButton.create(Component.literal("Почта"), ignored -> mailFriend())
				.bounds(rightX + columnWidth - actionWidth, 103, actionWidth, FIELD_HEIGHT)
				.tooltip(help(commandHelp("Почта", ActiveTemplateSnapshot.CommandSnapshot::mail))).build());

		friendAmountBox = new StyledEditBox(font, rightX, 128, columnWidth - actionWidth - 4, FIELD_HEIGHT,
				Component.literal("Сумма"));
		friendAmountBox.setMaxLength(16);
		friendAmountBox.setValue(friendAmountValue);
		friendAmountBox.setHint(Component.literal("сумма"));
		friendAmountBox.setResponder(value -> friendAmountValue = value);
		addRenderableWidget(friendAmountBox);
		addRenderableWidget(StyledButton.create(Component.literal("Деньги"), ignored -> payFriend())
				.bounds(rightX + columnWidth - actionWidth, 128, actionWidth, FIELD_HEIGHT)
				.tooltip(help(commandHelp("Перевод", ActiveTemplateSnapshot.CommandSnapshot::pay))).build());

		int halfActionWidth = (columnWidth - 4) / 2;
		addRenderableWidget(StyledButton.create(Component.literal("Отправить ТП"), ignored -> callFriend())
				.bounds(rightX, 153, halfActionWidth, FIELD_HEIGHT)
				.tooltip(help(commandHelp("Телепорт", ActiveTemplateSnapshot.CommandSnapshot::call))).build());

		StyledCycleButton<Boolean> hudToggle = StyledCycleButton.onOff(
				Boolean.TRUE.equals(config.friendHudEnabled),
				rightX + halfActionWidth + 4, 153, columnWidth - halfActionWidth - 4,
				FIELD_HEIGHT, Component.literal("HUD друзей"),
				(button, enabled) -> friendsController.setHudEnabled(enabled));
		addRenderableWidget(hudToggle);
		hudToggle.setTooltip(help("Показывать онлайн-друзей справа снизу во время игры"));

		if (config.teleportAutoAcceptMode == TeleportAutoAcceptMode.SELECTED_FRIENDS
				&& selectedFriend != null) {
			boolean enabled = config.teleportAutoAcceptFriends.stream()
					.anyMatch(friend -> friend.equalsIgnoreCase(selectedFriend));
			StyledCycleButton<Boolean> selectedToggle = StyledCycleButton.onOff(
					enabled, rightX, 178, columnWidth, FIELD_HEIGHT,
					Component.literal("Автоматически принимать телепорт"),
					(button, value) -> friendsController.setTeleportAutoAccept(selectedFriend, value));
			addRenderableWidget(selectedToggle);
			selectedToggle.setTooltip(help("Автоматически принимать запросы телепорта от выбранного друга"));
		}

		StyledCycleButton<TeleportAutoAcceptMode> autoAccept = StyledCycleButton.of(
				ResponderScreen::teleportAutoAcceptLabel, config.teleportAutoAcceptMode,
				List.of(TeleportAutoAcceptMode.OFF, TeleportAutoAcceptMode.EVERYONE,
						TeleportAutoAcceptMode.FRIENDS, TeleportAutoAcceptMode.SELECTED_FRIENDS),
				rightX, height - 38, columnWidth, FIELD_HEIGHT,
				Component.literal("Автоматически принимать телепорт"), (button, mode) -> {
					friendsController.setTeleportAutoAcceptMode(mode);
					rebuildContents();
				});
		addRenderableWidget(autoAccept);
		autoAccept.setTooltip(help("Выбрать, от кого автоматически принимать запросы телепорта"));

		refreshFriendSuggestions(friendNameValue);
	}

	private static Component teleportAutoAcceptLabel(TeleportAutoAcceptMode mode) {
		return Component.literal(switch (mode) {
			case OFF -> "Выкл";
			case EVERYONE -> "От всех";
			case FRIENDS -> "От друзей";
			case SELECTED_FRIENDS -> "От выбранных друзей";
		});
	}

	private int maxFriendPage(int pageSize) {
		return Pagination.maxPage(activeFriends().size(), pageSize);
	}

	private void addFriend() {
		String name = friendNameBox.getValue().trim();
		PlayerNameValidator.ValidationResult validation = PlayerNameValidator.validate(name);
		if (!validation.valid()) {
			setStatus(validation.errorMessage(), ERROR);
			return;
		}
		if (config.friends.stream().anyMatch(value -> value.equalsIgnoreCase(name))) {
			setStatus(name + " уже находится в друзьях", WARNING);
			return;
		}
		friendsController.add(name);
		selectedFriend = name;
		friendNameValue = "";
		if (CndlChatPlusClient.FRIEND_LOOKUP != null) {
			CndlChatPlusClient.FRIEND_LOOKUP.queueFriends(List.of(name));
		}
		setStatus("Добавлен друг: " + name, SUCCESS);
		rebuildContents();
	}

	private void removeFriend(String name) {
		friendsController.remove(name);
		if (name.equalsIgnoreCase(selectedFriend == null ? "" : selectedFriend)) {
			selectedFriend = null;
		}
		setStatus("Друг удалён: " + name, SUCCESS);
		rebuildContents();
	}

	private void sendPrivateToFriend() {
		if (!checkFriendAction()) {
			return;
		}
		String message = friendMessageBox.getValue().trim();
		if (message.isEmpty()) {
			setStatus("Введите текст личного сообщения", ERROR);
			return;
		}
		ServerCommandService.CommandResult result = CndlChatPlusClient.FRIEND_ACTIONS
				.privateMessage(selectedFriend, message);
		if (!result.success()) {
			setStatus(result.errorMessage(), ERROR);
			return;
		}
		friendMessageValue = "";
		friendMessageBox.setValue("");
		setStatus("ЛС отправлено: " + selectedFriend, SUCCESS);
	}

	private void payFriend() {
		if (!checkFriendAction()) {
			return;
		}
		AmountValidator.AmountValidationResult amountResult = AmountValidator.validate(friendAmountBox.getValue());
		if (!amountResult.valid()) {
			setStatus(amountResult.errorMessage(), ERROR);
			return;
		}
		ServerCommandService.CommandResult result = CndlChatPlusClient.FRIEND_ACTIONS
				.pay(selectedFriend, amountResult.normalizedAmount());
		if (!result.success()) {
			setStatus(result.errorMessage(), ERROR);
			return;
		}
		setStatus("Перевод отправлен: " + selectedFriend, SUCCESS);
	}

	private void callFriend() {
		if (!checkFriendAction()) {
			return;
		}
		ServerCommandService.CommandResult result = CndlChatPlusClient.FRIEND_ACTIONS.call(selectedFriend);
		if (!result.success()) {
			setStatus(result.errorMessage(), ERROR);
			return;
		}
		setStatus("Запрос телепорта отправлен: " + selectedFriend, SUCCESS);
	}

	private void mailFriend() {
		if (!checkFriendAction()) {
			return;
		}
		String message = friendMailBox.getValue().trim();
		if (message.isEmpty()) {
			setStatus("Введите текст сообщения на почту", ERROR);
			return;
		}
		ServerCommandService.CommandResult result = CndlChatPlusClient.FRIEND_ACTIONS.mail(selectedFriend, message);
		if (!result.success()) {
			setStatus(result.errorMessage(), ERROR);
			return;
		}
		friendMailValue = "";
		friendMailBox.setValue("");
		setStatus("Почта отправлена: " + selectedFriend, SUCCESS);
	}

	private String lastSeenFor(String friend) {
		return activeFriendLastSeen().entrySet().stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase(friend))
				.map(java.util.Map.Entry::getValue)
				.findFirst()
				.orElse("нет данных");
	}

	private boolean checkFriendAction() {
		if (selectedFriend == null) {
			setStatus("Сначала выберите друга из списка", ERROR);
			return false;
		}
		if (minecraft.getConnection() == null) {
			setStatus("Нет подключения к серверу", ERROR);
			return false;
		}
		return true;
	}

	private Set<String> currentOnlineFriends() {
		Set<String> result = new HashSet<>();
		if (minecraft == null || minecraft.getConnection() == null) {
			return result;
		}
		for (String friend : activeFriends()) {
			if (minecraft.getConnection().getPlayerInfoIgnoreCase(friend) != null) {
				result.add(friend.toLowerCase(Locale.ROOT));
			}
		}
		return result;
	}

	@Override
	public void tick() {
		if (tab != Tab.FRIENDS || ++friendOnlineRefreshTicks < 20) {
			return;
		}
		friendOnlineRefreshTicks = 0;
		Set<String> current = currentOnlineFriends();
		int currentLastSeenHash = activeFriendLastSeen().hashCode();
		if (!current.equals(onlineFriends) || currentLastSeenHash != friendLastSeenHash) {
			onlineFriends = current;
			friendLastSeenHash = currentLastSeenHash;
			rebuildContents();
		}
	}

	private List<String> activeFriends() {
		return CndlChatPlusClient.TEMPLATE_RUNTIME == null
				? config.friends
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::friends).orElse(List.of());
	}

	private java.util.Map<String, String> activeFriendLastSeen() {
		return CndlChatPlusClient.TEMPLATE_RUNTIME == null
				? config.friendLastSeen
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::friendLastSeen).orElse(java.util.Map.of());
	}

	private void refreshSuggestions(String query) {
		if (nicknameBox == null || minecraft == null || minecraft.getConnection() == null) {
			return;
		}

		List<String> names = suggestionProvider.suggest(minecraft, query, suggestionButtons.size());

		for (int index = 0; index < suggestionButtons.size(); index++) {
			Button button = suggestionButtons.get(index);
			button.visible = index < names.size();
			button.active = button.visible;
			if (button.visible) {
				button.setMessage(Component.literal(names.get(index)));
			}
		}
	}

	private void refreshFriendSuggestions(String query) {
		if (friendNameBox == null || minecraft == null || minecraft.getConnection() == null) {
			return;
		}

		List<String> names = suggestionProvider.suggest(minecraft, query, friendSuggestionButtons.size());

		for (int index = 0; index < friendSuggestionButtons.size(); index++) {
			Button button = friendSuggestionButtons.get(index);
			button.visible = index < names.size();
			button.active = button.visible;
			if (button.visible) {
				button.setMessage(Component.literal(names.get(index)));
			}
		}
	}

	private void addMutedPlayer() {
		String nickname = nicknameBox.getValue().trim();
		PlayerNameValidator.ValidationResult validation = PlayerNameValidator.validate(nickname);
		if (!validation.valid()) {
			setStatus(validation.errorMessage(), ERROR);
			return;
		}

		if (minecraft.getConnection() == null) {
			setStatus("Нет подключения к серверу", ERROR);
			return;
		}

		ServerCommandService.CommandResult result = CndlChatPlusClient.SERVER_COMMANDS.ignorePlayer(nickname);
		if (!result.success()) {
			setStatus(result.errorMessage(), ERROR);
			return;
		}
		setStatus("Отправлено: /ignoreplayer " + nickname, SUCCESS);
	}

	private void addDiscordMutedPlayer() {
		if (nicknameBox == null) {
			setStatus("Откройте вкладку «Ники»", ERROR);
			return;
		}
		String nickname = nicknameBox.getValue().trim();
		DiscordNameValidator.ValidationResult validation = DiscordNameValidator.validate(nickname);
		if (!validation.valid()) {
			setStatus(validation.errorMessage(), ERROR);
			return;
		}
		if (config.discordMutedPlayers.stream().anyMatch(value -> value.equalsIgnoreCase(nickname))) {
			setStatus(nickname + " уже находится в Discord-муте", WARNING);
			return;
		}

		blacklistController.addDiscord(nickname);
		nicknameValue = "";
		setStatus("Добавлен: " + nickname + " (discord)", SUCCESS);
		rebuildContents();
	}

	private void addMutedWord() {
		String word = wordBox.getValue().trim();
		if (word.isEmpty()) {
			setStatus("Введите слово или фразу", ERROR);
			return;
		}
		if (config.mutedWords.stream().anyMatch(value -> value.equalsIgnoreCase(word))) {
			setStatus("Это слово уже находится в списке", WARNING);
			return;
		}

		blacklistController.addWord(word);
		wordValue = "";
		setStatus("Слово добавлено в чёрный список", SUCCESS);
		rebuildContents();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (tab == Tab.FRIENDS && friendNameBox != null && friendNameBox.isFocused()
				&& event.key() == InputConstants.KEY_TAB && !friendSuggestionButtons.isEmpty()
				&& friendSuggestionButtons.getFirst().visible) {
			friendNameValue = friendSuggestionButtons.getFirst().getMessage().getString();
			friendNameBox.setValue(friendNameValue);
			return true;
		}
		if (tab == Tab.BLACKLIST && blacklistMode == BlacklistMode.NICKS
				&& nicknameBox != null && nicknameBox.isFocused()
				&& event.key() == InputConstants.KEY_TAB && !suggestionButtons.isEmpty()
				&& suggestionButtons.getFirst().visible) {
			nicknameValue = suggestionButtons.getFirst().getMessage().getString();
			nicknameBox.setValue(nicknameValue);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void renderBackgroundContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawBackground(graphics, width, height);
	}

	@Override
	protected void renderContent(CompatGraphics graphics, int mouseX, int mouseY, float delta) {
		ScreenChrome.drawPanel(graphics, panelX, 22, panelWidth, height - 26);
		int infoX = panelX + panelWidth / 2;
		graphics.text(font, title, infoX - font.width(title) - 4, 8, TEXT_COLOR);
		int half = panelWidth / 2;
		int tabIndex = tab.ordinal();
		int tabX = panelX + half * tabIndex;
		int tabWidth = tabIndex == 1 ? panelWidth - half : half;
		graphics.fill(tabX + 3, 47, tabX + tabWidth - 3, 49, ACCENT);
		if (tab == Tab.BLACKLIST) {
			if (blacklistMode == BlacklistMode.NICKS) {
				int columnWidth = (panelWidth - 46) / 2;
				graphics.text(font, "Подсказки игроков сервера", panelX + 18, 106, MUTED_COLOR);
				graphics.text(font, "Локальный мут Discord", panelX + 28 + columnWidth, 106, MUTED_COLOR);
			} else {
				graphics.text(font, "*-любое количество символов, например *нордпорт* работает что любое сообщение,",
						panelX + 18, 106, MUTED_COLOR);
				graphics.text(font, "где есть слово нордпорт не отобразится у вас в чате",
						panelX + 18, 116, MUTED_COLOR);
			}
		} else {
			int columnWidth = (panelWidth - 46) / 2;
			int rightX = panelX + 28 + columnWidth;
			graphics.text(font, "Список друзей", panelX + 18, 139, MUTED_COLOR);
			graphics.text(font, selectedFriend == null ? "Друг не выбран" : "Выбран: " + selectedFriend,
					rightX, 60, selectedFriend == null ? MUTED_COLOR : SUCCESS);
			if (selectedFriend != null) {
				boolean online = onlineFriends.contains(selectedFriend.toLowerCase(Locale.ROOT));
				graphics.text(font, online ? "Сейчас онлайн" : "Последний вход: " + lastSeenFor(selectedFriend),
						rightX, 68, online ? ONLINE : MUTED_COLOR);
			}
		}

		if (!status.empty()) {
			int statusY = height - 53;
			graphics.centeredText(font, status.text(), width / 2, statusY, status.color());
		}
		CreditRenderer.draw(graphics, font, panelX + 4, height - 13, MUTED_COLOR);
	}

	private static Tooltip help(String text) {
		return Tooltip.create(Component.literal(text));
	}

	private String commandHelp(String action,
			Function<ActiveTemplateSnapshot.CommandSnapshot, String> command) {
		String template = CndlChatPlusClient.TEMPLATE_RUNTIME == null ? null
				: CndlChatPlusClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::commands).map(command).orElse(null);
		return action + " — " + CommandTemplateDisplay.format(template);
	}

	private boolean saveCurrentTab() {
		return switch (tab) {
			case BLACKLIST -> blacklistController.save();
			case FRIENDS -> friendsController.save();
		};
	}

	private void setStatus(String text, int color) {
		status.set(text, color);
	}

	private void rebuildContents() {
		clearWidgets();
		suggestionButtons.clear();
		friendSuggestionButtons.clear();
		nicknameBox = null;
		wordBox = null;
		friendNameBox = null;
		friendMessageBox = null;
		friendMailBox = null;
		friendAmountBox = null;
		init();
	}

	@Override
	protected void repositionElements() {
		rebuildContents();
	}

	@Override
	public void removed() {
		saveCurrentTab();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum Tab {
		BLACKLIST("Чёрный список", "Настроить мут пользователей, Discord и слов"),
		FRIENDS("Друзья", "Сохранить друзей и использовать команды активного шаблона");

		private final String title;
		private final String help;

		Tab(String title, String help) {
			this.title = title;
			this.help = help;
		}
	}

	private enum BlacklistMode {
		NICKS,
		WORDS
	}

}
