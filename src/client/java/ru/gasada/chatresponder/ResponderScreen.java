package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

public final class ResponderScreen extends Screen {
	private static final int FIELD_HEIGHT = 20;
	private static final int ROW_HEIGHT = 28;
	private static final int PANEL_COLOR = 0xD9242B38;
	private static final int PANEL_BORDER = 0xFF536178;
	private static final int TEXT_COLOR = 0xFFE8ECF2;
	private static final int MUTED_COLOR = 0xFF9DA8B8;

	private final ResponderConfig config;
	private final List<Button> suggestionButtons = new ArrayList<>();
	private final List<Button> friendSuggestionButtons = new ArrayList<>();
	private Tab tab = Tab.RULES;
	private int page;
	private int pageSize;
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
	private String statusText = "";
	private int statusColor = 0xFF75D98B;

	public ResponderScreen(ResponderConfig config) {
		super(Component.literal("CNDL_chat+"));
		this.config = config;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(820, width - 20);
		panelX = (width - panelWidth) / 2;
		pageSize = Math.max(3, Math.min(8, (height - 126) / ROW_HEIGHT));

		int quarter = panelWidth / 4;
		addTabButton(Tab.RULES, panelX, 27, quarter);
		addTabButton(Tab.CHANNELS, panelX + quarter, 27, quarter);
		addTabButton(Tab.BLACKLIST, panelX + quarter * 2, 27, quarter);
		addTabButton(Tab.FRIENDS, panelX + quarter * 3, 27, panelWidth - quarter * 3);

		switch (tab) {
			case RULES -> initRulesTab();
			case CHANNELS -> initChannelsTab();
			case BLACKLIST -> initBlacklistTab();
			case FRIENDS -> initFriendsTab();
		}
	}

	private void addTabButton(Tab target, int x, int y, int buttonWidth) {
		Button button = addRenderableWidget(Button.builder(Component.literal(target.title), ignored -> {
			tab = target;
			statusText = "";
			rebuildContents();
		}).bounds(x, y, buttonWidth, 20).build());
		button.active = tab != target;
		button.setTooltip(help(target.help));
	}

	private void initRulesTab() {
		int maxPage = maxPage();
		page = Math.max(0, Math.min(page, maxPage));
		int start = page * pageSize;
		int end = Math.min(config.rules.size(), start + pageSize);
		int rowY = 67;

		for (int index = start; index < end; index++) {
			addRuleRow(config.rules.get(index), index, rowY);
			rowY += ROW_HEIGHT;
		}

		int bottomY = height - 38;
		CycleButton<Boolean> masterToggle = CycleButton.onOffBuilder(config.enabled)
				.create(panelX, bottomY, 70, FIELD_HEIGHT, Component.literal("Мод"),
						(button, enabled) -> config.enabled = enabled);
		addRenderableWidget(masterToggle);
		masterToggle.setTooltip(help("Полностью включает или выключает автоматические ответы"));

		addRenderableWidget(Button.builder(Component.literal("+ Правило"), ignored -> {
			config.rules.add(new ReplyRule("", "", ChatChannel.AUTO));
			page = maxPage();
			if (config.rules.size() > pageSize) {
				page = (config.rules.size() - 1) / pageSize;
			}
			rebuildContents();
		}).bounds(panelX + 75, bottomY, 90, FIELD_HEIGHT)
				.tooltip(help("Создать новое правило автоответа")).build());

		Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
			page--;
			rebuildContents();
		}).bounds(panelX + panelWidth / 2 - 65, bottomY, 30, FIELD_HEIGHT)
				.tooltip(help("Предыдущая страница правил")).build());
		previous.active = page > 0;

		Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
			page++;
			rebuildContents();
		}).bounds(panelX + panelWidth / 2 + 35, bottomY, 30, FIELD_HEIGHT)
				.tooltip(help("Следующая страница правил")).build());
		next.active = page < maxPage;

		int saveX = panelX + panelWidth - 90;
		addRenderableWidget(new InvisibleButton(0, 0, 12, 12,
				() -> minecraft.gui.setScreen(new PeriodicMessageScreen(this, config))));
		addRenderableWidget(Button.builder(Component.literal("Сохранить"), ignored -> saveConfig())
				.bounds(saveX, bottomY, 90, FIELD_HEIGHT)
				.tooltip(help("Сохранить все настройки мода")).build());
	}

	private void addRuleRow(ReplyRule rule, int ruleIndex, int y) {
		int x = panelX + 8;
		int available = panelWidth - 16;
		int enabledWidth = 52;
		int channelWidth = Math.min(92, Math.max(70, available / 8));
		int deleteWidth = 22;
		int fieldsWidth = available - enabledWidth - channelWidth - deleteWidth - 16;
		int triggerWidth = fieldsWidth / 2;
		int responseWidth = fieldsWidth - triggerWidth;

		CycleButton<Boolean> enabled = CycleButton.onOffBuilder(rule.enabled)
				.create(x, y, enabledWidth, FIELD_HEIGHT, Component.empty(),
						(button, value) -> rule.enabled = value);
		addRenderableWidget(enabled);
		enabled.setTooltip(help("Включить или выключить только это правило"));
		x += enabledWidth + 4;

		EditBox trigger = new EditBox(font, x, y, triggerWidth, FIELD_HEIGHT,
				Component.literal("Фраза-триггер"));
		trigger.setMaxLength(256);
		trigger.setValue(rule.trigger);
		trigger.setHint(Component.literal("например: привет*"));
		trigger.setResponder(value -> rule.trigger = value);
		addRenderableWidget(trigger);
		x += triggerWidth + 4;

		EditBox response = new EditBox(font, x, y, responseWidth, FIELD_HEIGHT,
				Component.literal("Ответ"));
		response.setMaxLength(256);
		response.setValue(rule.response);
		response.setHint(Component.literal("текст ответа"));
		response.setResponder(value -> rule.response = value);
		addRenderableWidget(response);
		x += responseWidth + 4;

		CycleButton<ChatChannel> channel = CycleButton.builder(
				value -> Component.literal(value.displayName()), rule.channel)
				.withValues(List.of(ChatChannel.values()))
				.displayOnlyValue()
				.create(x, y, channelWidth, FIELD_HEIGHT, Component.literal("Канал"),
						(button, value) -> rule.channel = value);
		addRenderableWidget(channel);
		channel.setTooltip(help("Выбрать тип входящего чата и канал ответа"));
		x += channelWidth + 4;

		addRenderableWidget(Button.builder(Component.literal("×"), ignored -> {
			config.rules.remove(ruleIndex);
			page = Math.min(page, maxPage());
			rebuildContents();
		}).bounds(x, y, deleteWidth, FIELD_HEIGHT)
				.tooltip(help("Удалить это правило")).build());
	}

	private void initChannelsTab() {
		int leftX = panelX + 18;
		int columnGap = 10;
		int fieldWidth = (panelWidth - 46) / 2;
		int rightX = leftX + fieldWidth + columnGap;

		EditBox globalPrefix = addTextField(leftX, 72, fieldWidth,
				"Префикс глобального ответа", config.globalPrefix, "!",
				value -> config.globalPrefix = value);
		globalPrefix.setMaxLength(16);

		EditBox clanPrefix = addTextField(leftX, 112, fieldWidth,
				"Префикс кланового ответа", config.clanReplyPrefix, "/.",
				value -> config.clanReplyPrefix = value);
		clanPrefix.setMaxLength(32);

		EditBox privateCommand = addTextField(leftX, 152, fieldWidth,
				"Команда ответа в ЛС", config.privateReplyCommand, "/r",
				value -> config.privateReplyCommand = value);
		privateCommand.setMaxLength(64);

		addTextField(rightX, 72, fieldWidth,
				"Маркеры глобального чата через запятую", config.globalMarkers,
				"[g],[global],[глобальный]", value -> config.globalMarkers = value);

		addTextField(rightX, 112, fieldWidth,
				"Маркеры кланового чата через запятую", config.clanMarkers,
				"(клан),〈клан〉", value -> config.clanMarkers = value);

		addTextField(rightX, 152, fieldWidth,
				"Маркеры личных сообщений через запятую", config.privateMarkers,
				"[pm],[лс],->,шепчет", value -> config.privateMarkers = value);

		addRenderableWidget(Button.builder(Component.literal("Сохранить"), ignored -> saveConfig())
				.bounds(panelX + panelWidth - 108, height - 38, 90, FIELD_HEIGHT)
				.tooltip(help("Сохранить префиксы и маркеры каналов")).build());
	}

	private EditBox addTextField(int x, int y, int fieldWidth, String narration, String value,
			String hint, java.util.function.Consumer<String> responder) {
		EditBox field = new EditBox(font, x, y, fieldWidth, FIELD_HEIGHT, Component.literal(narration));
		field.setMaxLength(512);
		field.setValue(value);
		field.setHint(Component.literal(hint));
		field.setResponder(responder);
		addRenderableWidget(field);
		return field;
	}

	private void initBlacklistTab() {
		int modeWidth = 100;
		int modesX = panelX + (panelWidth - modeWidth * 2) / 2;
		Button nickMode = addRenderableWidget(Button.builder(Component.literal("Ники"), ignored -> {
			blacklistMode = BlacklistMode.NICKS;
			statusText = "";
			rebuildContents();
		}).bounds(modesX, 55, modeWidth, FIELD_HEIGHT)
				.tooltip(help("Открыть мут обычных и Discord-пользователей")).build());
		nickMode.active = blacklistMode != BlacklistMode.NICKS;

		Button wordMode = addRenderableWidget(Button.builder(Component.literal("Слова"), ignored -> {
			blacklistMode = BlacklistMode.WORDS;
			statusText = "";
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
		CycleButton<Boolean> discordChat = CycleButton.builder(
				value -> Component.literal(value ? "Включён" : "Выключен"), config.discordChatEnabled)
				.withValues(false, true)
				.create(panelX + 18, height - 38, bottomButtonWidth, FIELD_HEIGHT,
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

		nicknameBox = new EditBox(font, x, 84, fieldWidth, FIELD_HEIGHT, Component.literal("Ник"));
		nicknameBox.setMaxLength(32);
		nicknameBox.setValue(nicknameValue);
		nicknameBox.setHint(Component.literal("ник игрока"));
		addRenderableWidget(nicknameBox);
		x += fieldWidth + 4;

		addRenderableWidget(Button.builder(Component.literal("Мут"), ignored -> addMutedPlayer())
				.bounds(x, 84, muteWidth, FIELD_HEIGHT)
				.tooltip(help("Отправить серверную команду /ignoreplayer для этого ника")).build());
		x += muteWidth + 4;

		addRenderableWidget(Button.builder(Component.literal("Мут Discord"), ignored -> addDiscordMutedPlayer())
				.bounds(x, 84, discordMuteWidth, FIELD_HEIGHT)
				.tooltip(help("Скрывать сообщения этого пользователя из Discord")).build());

		int columnGap = 10;
		int columnWidth = (panelWidth - 46) / 2;
		int listY = 118;
		int visibleRows = Math.max(2, Math.min(5, (height - 180) / 22));
		for (int index = 0; index < visibleRows; index++) {
			Button suggestion = Button.builder(Component.empty(), button -> {
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
			addRenderableWidget(Button.builder(Component.literal(name + " (discord)  ×"), ignored -> {
				config.discordMutedPlayers.removeIf(value -> value.equalsIgnoreCase(name));
				ConfigManager.save(config);
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
		wordBox = new EditBox(font, x, 84, panelWidth - 40 - addWidth, FIELD_HEIGHT,
				Component.literal("Скрываемое слово"));
		wordBox.setMaxLength(64);
		wordBox.setValue(wordValue);
		wordBox.setHint(Component.literal("слово, фраза или маска*"));
		wordBox.setResponder(value -> wordValue = value);
		addRenderableWidget(wordBox);

		addRenderableWidget(Button.builder(Component.literal("Мут"), ignored -> addMutedWord())
				.bounds(panelX + panelWidth - 18 - addWidth, 84, addWidth, FIELD_HEIGHT)
				.tooltip(help("Скрывать сообщения с введённым словом или фразой")).build());

		int visibleRows = Math.max(2, Math.min(5, (height - 180) / 22));
		int count = Math.min(visibleRows, config.mutedWords.size());
		for (int index = 0; index < count; index++) {
			String word = config.mutedWords.get(index);
			addRenderableWidget(Button.builder(Component.literal("\"" + word + "\"  ×"), ignored -> {
				config.mutedWords.removeIf(value -> value.equalsIgnoreCase(word));
				ConfigManager.save(config);
				rebuildContents();
			}).bounds(panelX + 18, 118 + index * 22, Math.min(400, panelWidth - 36), FIELD_HEIGHT)
					.tooltip(help("Нажмите, чтобы удалить слово из чёрного списка")).build());
		}
	}

	private void initFriendsTab() {
		onlineFriends = currentOnlineFriends();
		friendLastSeenHash = activeFriendLastSeen().hashCode();
		if (!friendLookupsQueued && GasadaChatResponderClient.FRIEND_LOOKUP != null) {
			GasadaChatResponderClient.FRIEND_LOOKUP.queueActiveFriends();
			friendLookupsQueued = true;
		}
		int columnGap = 10;
		int columnWidth = (panelWidth - 46) / 2;
		int leftX = panelX + 18;
		int rightX = leftX + columnWidth + columnGap;
		int addWidth = 82;

		friendNameBox = new EditBox(font, leftX, 60, columnWidth - addWidth - 4, FIELD_HEIGHT,
				Component.literal("Ник друга"));
		friendNameBox.setMaxLength(16);
		friendNameBox.setValue(friendNameValue);
		friendNameBox.setHint(Component.literal("ник будущего друга"));
		friendNameBox.setResponder(value -> {
			friendNameValue = value;
			refreshFriendSuggestions(value);
		});
		addRenderableWidget(friendNameBox);

		addRenderableWidget(Button.builder(Component.literal("Добавить"), ignored -> addFriend())
				.bounds(leftX + columnWidth - addWidth, 60, addWidth, FIELD_HEIGHT)
				.tooltip(help("Добавить введённый ник в сохранённый список друзей")).build());

		for (int index = 0; index < 2; index++) {
			Button suggestion = Button.builder(Component.empty(), button -> {
				friendNameValue = button.getMessage().getString();
				friendNameBox.setValue(friendNameValue);
			}).bounds(leftX, 86 + index * 22, columnWidth, FIELD_HEIGHT)
					.tooltip(help("Подставить этот онлайн-ник в поле друга")).build();
			suggestion.visible = false;
			friendSuggestionButtons.add(addRenderableWidget(suggestion));
		}

		int visibleRows = Math.max(2, Math.min(5, (height - 218) / 22));
		int maxPage = maxFriendPage(visibleRows);
		friendPage = Math.max(0, Math.min(friendPage, maxPage));
		int start = friendPage * visibleRows;
		List<String> activeFriends = activeFriends();
		int count = Math.min(visibleRows, activeFriends.size() - start);
		for (int offset = 0; offset < count; offset++) {
			String friend = activeFriends.get(start + offset);
			boolean online = onlineFriends.contains(friend.toLowerCase(Locale.ROOT));
			int y = 151 + offset * 22;
			String selection = friend.equalsIgnoreCase(selectedFriend == null ? "" : selectedFriend) ? "▶ " : "";
			String lastSeen = lastSeenFor(friend);
			String label = selection + friend + (online ? " — онлайн" : " — был: " + lastSeen);
			Component labelComponent = Component.literal(label).withColor(online ? 0x55FF55 : 0xA0A0A0);
			addRenderableWidget(Button.builder(labelComponent, ignored -> {
				selectedFriend = friend;
				setStatus("Выбран друг: " + friend, 0xFF75D98B);
				rebuildContents();
			}).bounds(leftX, y, columnWidth - 26, FIELD_HEIGHT)
					.tooltip(help(online ? "Друг сейчас онлайн"
							: "Последний раз был в сети: " + lastSeen)).build());
			addRenderableWidget(Button.builder(Component.literal("×"), ignored -> removeFriend(friend))
					.bounds(leftX + columnWidth - 22, y, 22, FIELD_HEIGHT)
					.tooltip(help("Удалить этого игрока из списка друзей")).build());
		}

		Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
			friendPage--;
			rebuildContents();
		}).bounds(leftX, height - 38, 30, FIELD_HEIGHT)
				.tooltip(help("Предыдущая страница списка друзей")).build());
		previous.active = friendPage > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
			friendPage++;
			rebuildContents();
		}).bounds(leftX + 36, height - 38, 30, FIELD_HEIGHT)
				.tooltip(help("Следующая страница списка друзей")).build());
		next.active = friendPage < maxPage;
		int actionWidth = 84;
		friendMessageBox = new EditBox(font, rightX, 78, columnWidth - actionWidth - 4, FIELD_HEIGHT,
				Component.literal("Личное сообщение"));
		friendMessageBox.setMaxLength(220);
		friendMessageBox.setValue(friendMessageValue);
		friendMessageBox.setHint(Component.literal("текст личного сообщения"));
		friendMessageBox.setResponder(value -> friendMessageValue = value);
		addRenderableWidget(friendMessageBox);
		addRenderableWidget(Button.builder(Component.literal("Отправить ЛС"), ignored -> sendPrivateToFriend())
				.bounds(rightX + columnWidth - actionWidth, 78, actionWidth, FIELD_HEIGHT)
				.tooltip(help("Отправить выбранному другу командой /w ник сообщение")).build());

		friendMailBox = new EditBox(font, rightX, 103, columnWidth - actionWidth - 4, FIELD_HEIGHT,
				Component.literal("Сообщение на почту"));
		friendMailBox.setMaxLength(220);
		friendMailBox.setValue(friendMailValue);
		friendMailBox.setHint(Component.literal("текст сообщения на почту"));
		friendMailBox.setResponder(value -> friendMailValue = value);
		addRenderableWidget(friendMailBox);
		addRenderableWidget(Button.builder(Component.literal("Почта"), ignored -> mailFriend())
				.bounds(rightX + columnWidth - actionWidth, 103, actionWidth, FIELD_HEIGHT)
				.tooltip(help("Отправить почту командой /mail send ник сообщение")).build());

		friendAmountBox = new EditBox(font, rightX, 128, columnWidth - actionWidth - 4, FIELD_HEIGHT,
				Component.literal("Сумма"));
		friendAmountBox.setMaxLength(16);
		friendAmountBox.setValue(friendAmountValue);
		friendAmountBox.setHint(Component.literal("сумма"));
		friendAmountBox.setResponder(value -> friendAmountValue = value);
		addRenderableWidget(friendAmountBox);
		addRenderableWidget(Button.builder(Component.literal("Деньги"), ignored -> payFriend())
				.bounds(rightX + columnWidth - actionWidth, 128, actionWidth, FIELD_HEIGHT)
				.tooltip(help("Перевести выбранному другу командой /pay ник сумма")).build());

		int halfActionWidth = (columnWidth - 4) / 2;
		addRenderableWidget(Button.builder(Component.literal("Отправить ТП"), ignored -> callFriend())
				.bounds(rightX, 153, halfActionWidth, FIELD_HEIGHT)
				.tooltip(help("Отправить выбранному другу запрос командой /call ник")).build());

		CycleButton<Boolean> hudToggle = CycleButton.onOffBuilder(Boolean.TRUE.equals(config.friendHudEnabled))
				.create(rightX + halfActionWidth + 4, 153, columnWidth - halfActionWidth - 4,
						FIELD_HEIGHT, Component.literal("HUD друзей"),
						(button, enabled) -> {
							config.friendHudEnabled = enabled;
							ConfigManager.save(config);
						});
		addRenderableWidget(hudToggle);
		hudToggle.setTooltip(help("Показывать онлайн-друзей справа снизу во время игры"));

		refreshFriendSuggestions(friendNameValue);
	}

	private int maxFriendPage(int pageSize) {
		return Math.max(0, (activeFriends().size() - 1) / pageSize);
	}

	private void addFriend() {
		String name = friendNameBox.getValue().trim();
		if (!name.matches("[A-Za-z0-9_]{1,16}")) {
			setStatus("Некорректный ник друга", 0xFFFF7777);
			return;
		}
		if (config.friends.stream().anyMatch(value -> value.equalsIgnoreCase(name))) {
			setStatus(name + " уже находится в друзьях", 0xFFFFCC66);
			return;
		}
		config.friends.add(name);
		selectedFriend = name;
		friendNameValue = "";
		ConfigManager.save(config);
		if (GasadaChatResponderClient.FRIEND_LOOKUP != null) {
			GasadaChatResponderClient.FRIEND_LOOKUP.queueFriends(List.of(name));
		}
		setStatus("Добавлен друг: " + name, 0xFF75D98B);
		rebuildContents();
	}

	private void removeFriend(String name) {
		config.friends.removeIf(value -> value.equalsIgnoreCase(name));
		config.friendLastSeen.keySet().removeIf(value -> value.equalsIgnoreCase(name));
		if (name.equalsIgnoreCase(selectedFriend == null ? "" : selectedFriend)) {
			selectedFriend = null;
		}
		ConfigManager.save(config);
		setStatus("Друг удалён: " + name, 0xFF75D98B);
		rebuildContents();
	}

	private void sendPrivateToFriend() {
		if (!checkFriendAction()) {
			return;
		}
		String message = friendMessageBox.getValue().trim();
		if (message.isEmpty()) {
			setStatus("Введите текст личного сообщения", 0xFFFF7777);
			return;
		}
		ServerCommandService.CommandResult result = GasadaChatResponderClient.FRIEND_ACTIONS
				.privateMessage(selectedFriend, message);
		if (!result.success()) {
			setStatus(result.errorMessage(), 0xFFFF7777);
			return;
		}
		friendMessageValue = "";
		friendMessageBox.setValue("");
		setStatus("ЛС отправлено: " + selectedFriend, 0xFF75D98B);
	}

	private void payFriend() {
		if (!checkFriendAction()) {
			return;
		}
		String amount = friendAmountBox.getValue().trim().replace(',', '.');
		if (!amount.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
			setStatus("Введите корректную сумму", 0xFFFF7777);
			return;
		}
		ServerCommandService.CommandResult result = GasadaChatResponderClient.FRIEND_ACTIONS.pay(selectedFriend, amount);
		if (!result.success()) {
			setStatus(result.errorMessage(), 0xFFFF7777);
			return;
		}
		setStatus("Перевод отправлен: " + selectedFriend, 0xFF75D98B);
	}

	private void callFriend() {
		if (!checkFriendAction()) {
			return;
		}
		ServerCommandService.CommandResult result = GasadaChatResponderClient.FRIEND_ACTIONS.call(selectedFriend);
		if (!result.success()) {
			setStatus(result.errorMessage(), 0xFFFF7777);
			return;
		}
		setStatus("Запрос телепорта отправлен: " + selectedFriend, 0xFF75D98B);
	}

	private void mailFriend() {
		if (!checkFriendAction()) {
			return;
		}
		String message = friendMailBox.getValue().trim();
		if (message.isEmpty()) {
			setStatus("Введите текст сообщения на почту", 0xFFFF7777);
			return;
		}
		ServerCommandService.CommandResult result = GasadaChatResponderClient.FRIEND_ACTIONS.mail(selectedFriend, message);
		if (!result.success()) {
			setStatus(result.errorMessage(), 0xFFFF7777);
			return;
		}
		friendMailValue = "";
		friendMailBox.setValue("");
		setStatus("Почта отправлена: " + selectedFriend, 0xFF75D98B);
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
			setStatus("Сначала выберите друга из списка", 0xFFFF7777);
			return false;
		}
		if (minecraft.getConnection() == null) {
			setStatus("Нет подключения к серверу", 0xFFFF7777);
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
		return GasadaChatResponderClient.TEMPLATE_RUNTIME == null
				? config.friends
				: GasadaChatResponderClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::friends).orElse(List.of());
	}

	private java.util.Map<String, String> activeFriendLastSeen() {
		return GasadaChatResponderClient.TEMPLATE_RUNTIME == null
				? config.friendLastSeen
				: GasadaChatResponderClient.TEMPLATE_RUNTIME.activeSnapshot()
						.map(ActiveTemplateSnapshot::friendLastSeen).orElse(java.util.Map.of());
	}

	private void refreshSuggestions(String query) {
		if (nicknameBox == null || minecraft == null || minecraft.getConnection() == null) {
			return;
		}

		String normalizedQuery = query.toLowerCase(Locale.ROOT);
		String ownName = minecraft.getUser().getName();
		List<String> names = minecraft.getConnection().getListedOnlinePlayers().stream()
				.map(PlayerInfo::getProfile)
				.map(profile -> profile.name())
				.filter(name -> !name.equalsIgnoreCase(ownName))
				.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalizedQuery))
				.sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
				.distinct()
				.limit(suggestionButtons.size())
				.toList();

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

		String normalizedQuery = query.toLowerCase(Locale.ROOT);
		String ownName = minecraft.getUser().getName();
		List<String> names = minecraft.getConnection().getListedOnlinePlayers().stream()
				.map(PlayerInfo::getProfile)
				.map(profile -> profile.name())
				.filter(name -> !name.equalsIgnoreCase(ownName))
				.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalizedQuery))
				.sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
				.distinct()
				.limit(friendSuggestionButtons.size())
				.toList();

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
		if (!nickname.matches("[A-Za-z0-9_]{1,16}")) {
			setStatus("Некорректный ник", 0xFFFF7777);
			return;
		}

		if (minecraft.getConnection() == null) {
			setStatus("Нет подключения к серверу", 0xFFFF7777);
			return;
		}

		ServerCommandService.CommandResult result = GasadaChatResponderClient.SERVER_COMMANDS.ignorePlayer(nickname);
		if (!result.success()) {
			setStatus(result.errorMessage(), 0xFFFF7777);
			return;
		}
		setStatus("Отправлено: /ignoreplayer " + nickname, 0xFF75D98B);
	}

	private void addDiscordMutedPlayer() {
		if (nicknameBox == null) {
			setStatus("Откройте вкладку «Ники»", 0xFFFF7777);
			return;
		}
		String nickname = nicknameBox.getValue().trim();
		if (!nickname.matches("[\\p{L}\\p{N}_]{1,32}")) {
			setStatus("Некорректный Discord-ник", 0xFFFF7777);
			return;
		}
		if (config.discordMutedPlayers.stream().anyMatch(value -> value.equalsIgnoreCase(nickname))) {
			setStatus(nickname + " уже находится в Discord-муте", 0xFFFFCC66);
			return;
		}

		config.discordMutedPlayers.add(nickname);
		nicknameValue = "";
		ConfigManager.save(config);
		setStatus("Добавлен: " + nickname + " (discord)", 0xFF75D98B);
		rebuildContents();
	}

	private void addMutedWord() {
		String word = wordBox.getValue().trim();
		if (word.isEmpty()) {
			setStatus("Введите слово или фразу", 0xFFFF7777);
			return;
		}
		if (config.mutedWords.stream().anyMatch(value -> value.equalsIgnoreCase(word))) {
			setStatus("Это слово уже находится в списке", 0xFFFFCC66);
			return;
		}

		config.mutedWords.add(word);
		wordValue = "";
		ConfigManager.save(config);
		setStatus("Слово добавлено в чёрный список", 0xFF75D98B);
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
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0xE010141D);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(panelX - 2, 20, panelX + panelWidth + 2, height - 2, PANEL_BORDER);
		graphics.fill(panelX, 22, panelX + panelWidth, height - 4, PANEL_COLOR);
		graphics.centeredText(font, title, width / 2, 8, TEXT_COLOR);

		if (tab == Tab.RULES) {
			graphics.text(font, "Слева — входящая фраза (* означает любой текст), справа — ответ", panelX + 8, 53, MUTED_COLOR);
			graphics.centeredText(font, (page + 1) + " / " + (maxPage() + 1), width / 2, height - 32, MUTED_COLOR);
		} else if (tab == Tab.CHANNELS) {
			int leftX = panelX + 18;
			int rightX = leftX + (panelWidth - 46) / 2 + 10;
			drawFieldLabel(graphics, "Глобальный ответ", leftX, 58);
			drawFieldLabel(graphics, "Клановый ответ", leftX, 98);
			drawFieldLabel(graphics, "Ответ в личные сообщения", leftX, 138);
			drawFieldLabel(graphics, "Маркеры глобального чата", rightX, 58);
			drawFieldLabel(graphics, "Маркеры кланового чата", rightX, 98);
			drawFieldLabel(graphics, "Маркеры личных сообщений", rightX, 138);
			if (statusText.isEmpty()) {
				graphics.text(font, "Локальный ответ идёт без префикса. Первое подходящее правило имеет приоритет.",
						panelX + 18, 181, MUTED_COLOR);
			}
		} else if (tab == Tab.BLACKLIST) {
			if (blacklistMode == BlacklistMode.NICKS) {
				int columnWidth = (panelWidth - 46) / 2;
				graphics.text(font, "Подсказки игроков сервера", panelX + 18, 106, MUTED_COLOR);
				graphics.text(font, "Локальный мут Discord", panelX + 28 + columnWidth, 106, MUTED_COLOR);
			} else {
				graphics.text(font, "Сообщения с любым словом или фразой из списка будут скрыты",
						panelX + 18, 106, MUTED_COLOR);
			}
		} else {
			int columnWidth = (panelWidth - 46) / 2;
			int rightX = panelX + 28 + columnWidth;
			graphics.text(font, "Список друзей", panelX + 18, 139, MUTED_COLOR);
			graphics.text(font, selectedFriend == null ? "Друг не выбран" : "Выбран: " + selectedFriend,
					rightX, 60, selectedFriend == null ? MUTED_COLOR : 0xFF75D98B);
			if (selectedFriend != null) {
				boolean online = onlineFriends.contains(selectedFriend.toLowerCase(Locale.ROOT));
				graphics.text(font, online ? "Сейчас онлайн" : "Последний вход: " + lastSeenFor(selectedFriend),
						rightX, 68, online ? 0xFF55FF55 : MUTED_COLOR);
			}
		}

		if (!statusText.isEmpty()) {
			int statusY = height - 53;
			graphics.centeredText(font, statusText, width / 2, statusY, statusColor);
		}
		CreditRenderer.draw(graphics, font, panelX + 4, height - 13, MUTED_COLOR);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawFieldLabel(GuiGraphicsExtractor graphics, String text, int x, int y) {
		graphics.text(font, text, x, y, MUTED_COLOR);
	}

	private static Tooltip help(String text) {
		return Tooltip.create(Component.literal(text));
	}

	private int maxPage() {
		return Math.max(0, (config.rules.size() - 1) / Math.max(1, pageSize));
	}

	private void saveConfig() {
		boolean saved = ConfigManager.save(config);
		setStatus(saved ? "Настройки сохранены" : "Ошибка сохранения",
				saved ? 0xFF75D98B : 0xFFFF7777);
	}

	private void setStatus(String text, int color) {
		statusText = text;
		statusColor = color;
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
		ConfigManager.save(config);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum Tab {
		RULES("Правила автоответа", "Настроить фразы, ответы и типы чата"),
		CHANNELS("Каналы", "Настроить префиксы и распознавание каналов"),
		BLACKLIST("Чёрный список", "Настроить мут пользователей, Discord и слов"),
		FRIENDS("Друзья", "Сохранить друзей и быстро использовать /w, /pay, /call и /mail send");

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

	private static final class InvisibleButton extends AbstractWidget {
		private final Runnable action;

		private InvisibleButton(int x, int y, int width, int height, Runnable action) {
			super(x, y, width, height, Component.empty());
			this.action = action;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			action.run();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
		}
	}
}
