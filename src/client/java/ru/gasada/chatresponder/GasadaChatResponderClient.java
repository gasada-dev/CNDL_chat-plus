package ru.gasada.chatresponder;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GasadaChatResponderClient implements ClientModInitializer {
	public static final String MOD_ID = "gasada_chat_responder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static ResponderConfig CONFIG;
	public static FriendLookupManager FRIEND_LOOKUP;
	public static ServerTemplateRuntime TEMPLATE_RUNTIME;
	public static ServerCommandService SERVER_COMMANDS;
	public static FriendActionService FRIEND_ACTIONS;
	public static TemplateSelectionService TEMPLATE_SELECTION;
	public static TemplateCatalogService TEMPLATE_CATALOG;
	public static PlayerInfoService PLAYER_INFO;
	public static MarriageLookupManager MARRIAGE_LOOKUP;
	public static ChatTabController CHAT_TABS;
	public static ChatTimestamps CHAT_TIMESTAMPS;
	public static ChatSearchState CHAT_SEARCH;
	private ChatVisibilityFilter visibilityFilter;

	@Override
	public void onInitializeClient() {
		CONFIG = ConfigManager.load();
		ChatResponderEngine engine = new ChatResponderEngine(CONFIG);
		UpdateChecker updateChecker = new UpdateChecker();
		TemplateSwitchCoordinator switchCoordinator = new TemplateSwitchCoordinator();
		switchCoordinator.register(engine::resetRuntimeState);
		TEMPLATE_RUNTIME = new ServerTemplateRuntime(switchCoordinator);
		TEMPLATE_RUNTIME.switchTo(LegacyConfigToVanillaBoxMigration.fromLegacy(CONFIG));
		ServerTemplateRepository templateRepository = ConfigManager.templateRepository();
		TemplateOperationResult<RootConfigSchemaMigration.MigrationReport> schemaMigration =
				new RootConfigSchemaMigration(templateRepository).migrate();
		if (!schemaMigration.success()) {
			LOGGER.warn("Не удалось обновить схему шаблонов: {}", schemaMigration.errorMessage());
		} else if (!schemaMigration.value().warnings().isEmpty()) {
			LOGGER.warn("Миграция шаблонов завершена с предупреждениями: {}",
					String.join("; ", schemaMigration.value().warnings()));
		}
		TEMPLATE_CATALOG = new TemplateCatalogService(templateRepository, ConfigManager.templateImportDirectory());
		TemplateCatalogService.ImportSummary bundled = TEMPLATE_CATALOG.installBundledTemplates();
		if (!bundled.success()) {
			LOGGER.warn("Не все встроенные шаблоны установлены: {}", String.join("; ", bundled.errors()));
		}
		TEMPLATE_SELECTION = new TemplateSelectionService(templateRepository, TEMPLATE_RUNTIME, CONFIG);
		TemplateOperationResult<ServerTemplate> initialTemplate = TEMPLATE_SELECTION.initializeDefault();
		if (!initialTemplate.success()) {
			LOGGER.warn("Не удалось выбрать начальный шаблон: {}", initialTemplate.errorMessage());
		}
		engine.setTemplateRuntime(TEMPLATE_RUNTIME);
		SERVER_COMMANDS = new ServerCommandService(TEMPLATE_RUNTIME, engine.outgoingChatService());
		PeriodicMessageScheduler periodicScheduler = new PeriodicMessageScheduler(
				TEMPLATE_RUNTIME, engine.outgoingChatService());
		switchCoordinator.register(periodicScheduler::resetRuntimeState);
		FRIEND_ACTIONS = new FriendActionService(TEMPLATE_RUNTIME, SERVER_COMMANDS, CONFIG);
		visibilityFilter = new ChatVisibilityFilter(TEMPLATE_RUNTIME);
		ServerLookupCoordinator lookupCoordinator = new ServerLookupCoordinator();
		FRIEND_LOOKUP = new FriendLookupManager(TEMPLATE_RUNTIME, FRIEND_ACTIONS, System::currentTimeMillis,
				lookupCoordinator);
		MARRIAGE_LOOKUP = new MarriageLookupManager(TEMPLATE_RUNTIME, SERVER_COMMANDS, lookupCoordinator);
		PLAYER_INFO = new PlayerInfoService(TEMPLATE_RUNTIME, new VanillaGameProfileClient(), FRIEND_LOOKUP,
				MARRIAGE_LOOKUP,
				runnable -> net.minecraft.client.Minecraft.getInstance().execute(runnable));
		switchCoordinator.register(PLAYER_INFO::resetRuntimeState);
		FriendsHud friendsHud = new FriendsHud(TEMPLATE_RUNTIME);
		switchCoordinator.register(friendsHud::resetRuntimeState);
		switchCoordinator.register(FRIEND_LOOKUP::resetRuntimeState);
		switchCoordinator.register(MARRIAGE_LOOKUP::resetRuntimeState);
		friendsHud.register();

		ChatMessageStore chatMessageStore = new ChatMessageStore(() -> CONFIG.chatHistoryLimit);
		ChatHistoryStore chatHistoryStore = new ChatHistoryStore(ConfigManager.chatHistoryDirectory());
		ChatHistoryCodec chatHistoryCodec = new ChatHistoryCodec();
		CHAT_TIMESTAMPS = new ChatTimestamps(() -> Boolean.TRUE.equals(CONFIG.chatTimestampsEnabled));
		CHAT_TABS = new ChatTabController(new ChatTabClassifier(TEMPLATE_RUNTIME),
				() -> Boolean.TRUE.equals(CONFIG.chatTabsEnabled));
		CHAT_SEARCH = new ChatSearchState(() -> Boolean.TRUE.equals(CONFIG.chatSearchEnabled));
		switchCoordinator.register(CHAT_TABS::resetRuntimeState);
		switchCoordinator.register(CHAT_TIMESTAMPS::resetRuntimeState);

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping openScreen = PlatformKeyMapping.register(new KeyMapping(
				"key.gasada_chat_responder.open",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_F8,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			TEMPLATE_SELECTION.tick(minecraft);
			PLAYER_INFO.tick(minecraft);
			while (openScreen.consumeClick()) {
				ClientUi.setScreen(minecraft, new ResponderScreen(CONFIG));
			}
			periodicScheduler.tick(minecraft);
			MARRIAGE_LOOKUP.tick(minecraft);
			FRIEND_LOOKUP.tick(minecraft);
			friendsHud.tick(minecraft);
			updateChecker.tick(minecraft);
		});

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, chatType, timestamp) ->
				MARRIAGE_LOOKUP.shouldShowSystemMessage(message, false)
						&& FRIEND_LOOKUP.shouldShowSystemMessage(message, false)
						&& visibilityFilter.decide(message.getString(), sender == null ? null : sender.name()).visible());
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
				overlay || MARRIAGE_LOOKUP.shouldShowSystemMessage(message, false)
						&& FRIEND_LOOKUP.shouldShowSystemMessage(message, false)
						&& visibilityFilter.decide(message.getString()).visible());

		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, chatType, timestamp) -> {
			recordIncoming(chatMessageStore, chatHistoryCodec, message, false);
			engine.handlePlayerMessage(message, signedMessage, sender);
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				recordIncoming(chatMessageStore, chatHistoryCodec, message, true);
			}
			engine.handleSystemMessage(message, overlay);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) ->
				restoreChatHistory(chatMessageStore, chatHistoryStore, chatHistoryCodec, minecraft));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> {
			saveChatHistory(chatMessageStore, chatHistoryStore, minecraft);
			CHAT_TABS.resetRuntimeState();
			CHAT_TIMESTAMPS.resetRuntimeState();
		});
	}

	private static void recordIncoming(ChatMessageStore store, ChatHistoryCodec codec, Component message,
			boolean fromGame) {
		if (CHAT_TABS.enabled()) {
			CHAT_TABS.recordMessage(message, fromGame);
		}
		if (Boolean.TRUE.equals(CONFIG.chatHistoryEnabled)) {
			String json = codec.toJson(message);
			if (json != null) {
				ChatTab tab = CHAT_TABS.classify(message.getString(), fromGame);
				store.add(new ChatHistoryEntry(System.currentTimeMillis(), json, tab));
			}
		}
	}

	private static void restoreChatHistory(ChatMessageStore store, ChatHistoryStore historyStore,
			ChatHistoryCodec codec, Minecraft minecraft) {
		if (!Boolean.TRUE.equals(CONFIG.chatHistoryEnabled) || !Boolean.TRUE.equals(CONFIG.chatHistoryPersist)) {
			return;
		}
		String key = currentServerKey(minecraft);
		if (key == null) {
			return;
		}
		List<ChatHistoryEntry> entries = historyStore.load(key);
		if (entries.size() > CONFIG.chatHistoryLimit) {
			entries = entries.subList(entries.size() - CONFIG.chatHistoryLimit, entries.size());
		}
		for (ChatHistoryEntry entry : entries) {
			Component component = codec.fromJson(entry.json());
			if (component != null) {
				Component stamped = CHAT_TIMESTAMPS.restored(component, entry.timestamp());
				CHAT_TABS.mapSource(stamped, entry.tab() == ChatTab.SYSTEM);
				ChatAccess.addMessage(ChatAccess.chat(minecraft), stamped);
				store.add(entry);
			}
		}
	}

	private static void saveChatHistory(ChatMessageStore store, ChatHistoryStore historyStore, Minecraft minecraft) {
		String key = Boolean.TRUE.equals(CONFIG.chatHistoryEnabled)
				&& Boolean.TRUE.equals(CONFIG.chatHistoryPersist) ? currentServerKey(minecraft) : null;
		if (key != null && !historyStore.save(key, store.snapshot())) {
			LOGGER.warn("История чата для текущего сервера не сохранена");
		}
		store.clear();
	}

	private static String currentServerKey(Minecraft minecraft) {
		ServerData server = minecraft.getCurrentServer();
		if (server == null) {
			return null;
		}
		ServerAddressNormalizer.NormalizationResult normalized = ServerAddressNormalizer.normalize(server.ip);
		return normalized.valid() ? ChatHistoryStore.fileKey(normalized.normalizedAddress()) : null;
	}

}
