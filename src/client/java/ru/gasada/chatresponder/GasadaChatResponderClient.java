package ru.gasada.chatresponder;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

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
	private static final Pattern DISCORD_MARKER = Pattern.compile(
			"(?iu)(?:\\(|\\[|<|\\{|«|‹|〈)\\s*discord\\s*(?:\\)|\\]|>|\\}|»|›|〉)");
	private static final Pattern DISCORD_NAME = Pattern.compile("[\\p{L}\\p{N}_]{1,32}");
	public static ResponderConfig CONFIG;

	@Override
	public void onInitializeClient() {
		CONFIG = ConfigManager.load();
		ChatResponderEngine engine = new ChatResponderEngine(CONFIG);
		PeriodicMessageScheduler periodicScheduler = new PeriodicMessageScheduler(CONFIG, engine);

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
		});

		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, chatType, timestamp) ->
				shouldShowDiscordMessage(message));
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
				overlay || shouldShowDiscordMessage(message));

		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, chatType, timestamp) ->
				engine.handlePlayerMessage(message, signedMessage, sender));
		ClientReceiveMessageEvents.GAME.register(engine::handleSystemMessage);
	}

	private static boolean shouldShowDiscordMessage(Component message) {
		String text = message.getString();
		Matcher discordMarker = DISCORD_MARKER.matcher(text);
		boolean discordMessage = discordMarker.find();
		if (discordMessage && !Boolean.TRUE.equals(CONFIG.discordChatEnabled)) {
			return false;
		}

		if (discordMessage) {
			String sender = extractDiscordSender(text, discordMarker.end());
			if (sender != null && CONFIG.discordMutedPlayers.stream()
					.anyMatch(name -> name.equalsIgnoreCase(sender))) {
				return false;
			}
		}

		String normalizedText = text.toLowerCase(java.util.Locale.ROOT);
		return CONFIG.mutedWords.stream()
				.noneMatch(word -> matchesMutedPattern(word, normalizedText));
	}

	private static boolean matchesMutedPattern(String wildcard, String normalizedText) {
		String normalizedWildcard = wildcard.toLowerCase(java.util.Locale.ROOT).trim();
		if (!normalizedWildcard.contains("*")) {
			return normalizedText.contains(normalizedWildcard);
		}

		StringBuilder regex = new StringBuilder();
		int start = 0;
		for (int index = 0; index < normalizedWildcard.length(); index++) {
			if (normalizedWildcard.charAt(index) == '*') {
				regex.append(Pattern.quote(normalizedWildcard.substring(start, index))).append(".*");
				start = index + 1;
			}
		}
		regex.append(Pattern.quote(normalizedWildcard.substring(start)));
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL)
				.matcher(normalizedText).find();
	}

	private static String extractDiscordSender(String text, int markerEnd) {
		int separator = text.indexOf('»', markerEnd);
		String authorPart = separator >= 0 ? text.substring(markerEnd, separator) : text.substring(markerEnd);
		Matcher names = DISCORD_NAME.matcher(authorPart);
		if (separator < 0) {
			return names.find() ? names.group() : null;
		}
		String lastName = null;
		while (names.find()) {
			lastName = names.group();
		}
		return lastName;
	}
}
