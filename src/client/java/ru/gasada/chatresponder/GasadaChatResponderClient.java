package ru.gasada.chatresponder;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GasadaChatResponderClient implements ClientModInitializer {
	public static final String MOD_ID = "gasada_chat_responder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final WildcardMatcher MUTED_WORD_MATCHER = new WildcardMatcher();
	public static ResponderConfig CONFIG;
	public static FriendLookupManager FRIEND_LOOKUP;
	public static ServerTemplateRuntime TEMPLATE_RUNTIME;
	public static ServerCommandService SERVER_COMMANDS;

	@Override
	public void onInitializeClient() {
		CONFIG = ConfigManager.load();
		ChatResponderEngine engine = new ChatResponderEngine(CONFIG);
		PeriodicMessageScheduler periodicScheduler = new PeriodicMessageScheduler(CONFIG, engine);
		UpdateChecker updateChecker = new UpdateChecker();
		TemplateSwitchCoordinator switchCoordinator = new TemplateSwitchCoordinator();
		switchCoordinator.register(engine::resetRuntimeState);
		switchCoordinator.register(FriendsHud::resetRuntimeState);
		switchCoordinator.register(periodicScheduler::resetRuntimeState);
		switchCoordinator.register(MUTED_WORD_MATCHER::clear);
		TEMPLATE_RUNTIME = new ServerTemplateRuntime(switchCoordinator);
		TEMPLATE_RUNTIME.switchTo(LegacyConfigToVanillaBoxMigration.fromLegacy(CONFIG));
		engine.setTemplateRuntime(TEMPLATE_RUNTIME);
		SERVER_COMMANDS = new ServerCommandService(TEMPLATE_RUNTIME, engine.outgoingChatService());
		FRIEND_LOOKUP = new FriendLookupManager(CONFIG, SERVER_COMMANDS, TEMPLATE_RUNTIME);
		switchCoordinator.register(FRIEND_LOOKUP::resetRuntimeState);
		FriendsHud.register(CONFIG);

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		KeyMapping openScreen = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.gasada_chat_responder.open",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_F8,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (openScreen.consumeClick()) {
				minecraft.gui.setScreen(new ResponderScreen(CONFIG));
			}
			periodicScheduler.tick(minecraft);
			FRIEND_LOOKUP.tick(minecraft);
			updateChecker.tick(minecraft);
		});

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, chatType, timestamp) ->
				FRIEND_LOOKUP.shouldShowSystemMessage(message, false) && shouldShowDiscordMessage(message));
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
				overlay || FRIEND_LOOKUP.shouldShowSystemMessage(message, false)
						&& shouldShowDiscordMessage(message));

		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, chatType, timestamp) ->
				engine.handlePlayerMessage(message, signedMessage, sender));
		ClientReceiveMessageEvents.GAME.register(engine::handleSystemMessage);
	}

	private static boolean shouldShowDiscordMessage(Component message) {
		String text = message.getString();
		DiscordMessageParser.DiscordMessageInfo discord = TEMPLATE_RUNTIME.compiledParsers()
				.map(DiscordMessageParser::new)
				.map(parser -> parser.parse(text))
				.orElse(new DiscordMessageParser.DiscordMessageInfo(false, null));
		if (discord.discordMessage() && !Boolean.TRUE.equals(CONFIG.discordChatEnabled)) {
			return false;
		}

		if (discord.discordMessage()) {
			if (discord.sender() != null && CONFIG.discordMutedPlayers.stream()
					.anyMatch(name -> name.equalsIgnoreCase(discord.sender()))) {
				return false;
			}
		}

		return !matchesAnyMutedPattern(CONFIG.mutedWords, text);
	}

	static boolean matchesAnyMutedPattern(List<String> mutedWords, String text) {
		return mutedWords.stream().anyMatch(word -> matchesMutedPattern(word, text));
	}

	static boolean matchesMutedPattern(String wildcard, String text) {
		return MUTED_WORD_MATCHER.matches(wildcard, text, WildcardMatchMode.CONTAINS_MATCH);
	}

}
