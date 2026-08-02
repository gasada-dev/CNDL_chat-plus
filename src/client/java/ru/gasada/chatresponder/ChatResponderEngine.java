package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;

public final class ChatResponderEngine {
	private static final long DUPLICATE_WINDOW_MS = 400;
	private static final long OWN_MESSAGE_WINDOW_MS = 5_000;

	private final ResponderConfig config;
	private String lastIncomingFingerprint = "";
	private long lastIncomingAt;
	private String lastSentText = "";
	private long lastSentAt;

	public ChatResponderEngine(ResponderConfig config) {
		this.config = config;
	}

	public void handlePlayerMessage(Component displayedMessage, PlayerChatMessage signedMessage, GameProfile sender) {
		Minecraft minecraft = Minecraft.getInstance();
		if (sender != null && minecraft.isLocalPlayer(sender.id())) {
			return;
		}

		String content = signedMessage != null ? signedMessage.signedContent() : displayedMessage.getString();
		handleIncoming(content, displayedMessage.getString());
	}

	public void handleSystemMessage(Component message, boolean overlay) {
		if (!overlay) {
			handleIncoming(message.getString(), message.getString());
		}
	}

	private void handleIncoming(String content, String displayed) {
		if (!config.enabled || content == null || displayed == null) {
			return;
		}
		if (isLikelyOwnDisplayedMessage(displayed)) {
			return;
		}

		long now = System.currentTimeMillis();
		String fingerprint = normalize(content) + '\n' + normalize(displayed);
		if (fingerprint.equals(lastIncomingFingerprint) && now - lastIncomingAt < DUPLICATE_WINDOW_MS) {
			return;
		}
		lastIncomingFingerprint = fingerprint;
		lastIncomingAt = now;

		if (isRecentOwnMessage(content, displayed, now)) {
			return;
		}

		ChatChannel detectedChannel = detectChannel(content, displayed);
		List<String> candidates = buildCandidates(content, displayed, detectedChannel);
		ReplyRule rule = findFirstMatchingRule(config.rules, detectedChannel, candidates);
		if (rule != null) {
			ChatChannel replyChannel = rule.channel == ChatChannel.AUTO ? detectedChannel : rule.channel;
			sendReply(rule.response, replyChannel);
		}
	}

	static ReplyRule findFirstMatchingRule(List<ReplyRule> rules, ChatChannel detectedChannel,
			List<String> candidates) {
		for (ReplyRule rule : rules) {
			if (!rule.enabled || rule.trigger.isBlank() || rule.response.isBlank()) {
				continue;
			}
			if (rule.channel != ChatChannel.AUTO && rule.channel != detectedChannel) {
				continue;
			}

			if (candidates.stream().anyMatch(candidate -> wildcardMatches(rule.trigger, candidate))) {
				return rule;
			}
		}
		return null;
	}

	ChatChannel detectChannel(String content, String displayed) {
		String normalizedContent = normalize(content);
		String normalizedDisplayed = normalize(displayed);

		// Сообщения Discord-моста на этом сервере всегда относятся к глобальному чату.
		if (isDiscordMessage(normalizedDisplayed)) {
			return ChatChannel.GLOBAL;
		}
		if (containsAnyMarker(normalizedDisplayed, config.privateMarkers)) {
			return ChatChannel.PRIVATE;
		}
		if (containsAnyMarker(normalizedDisplayed, config.clanMarkers)) {
			return ChatChannel.CLAN;
		}
		if (!config.globalPrefix.isBlank() && normalizedContent.startsWith(normalize(config.globalPrefix))) {
			return ChatChannel.GLOBAL;
		}
		if (normalizedDisplayed.contains("(!)")
				|| containsAnyMarker(normalizedDisplayed, config.globalMarkers)) {
			return ChatChannel.GLOBAL;
		}
		return ChatChannel.LOCAL;
	}

	private static boolean isDiscordMessage(String text) {
		return text.matches(".*(?:\\(|\\[|<|\\{|«|‹|〈)\\s*discord\\s*(?:\\)|\\]|>|\\}|»|›|〉).*");
	}

	private static boolean containsAnyMarker(String text, String commaSeparatedMarkers) {
		for (String marker : commaSeparatedMarkers.split(",")) {
			String normalizedMarker = normalize(marker);
			if (!normalizedMarker.isEmpty() && text.contains(normalizedMarker)) {
				return true;
			}
		}
		return false;
	}

	private List<String> buildCandidates(String content, String displayed, ChatChannel channel) {
		Set<String> candidates = new LinkedHashSet<>();
		addCandidate(candidates, content);
		addCandidate(candidates, displayed);

		if (channel == ChatChannel.GLOBAL && !config.globalPrefix.isBlank()) {
			String normalized = normalize(content);
			String prefix = normalize(config.globalPrefix);
			if (normalized.startsWith(prefix)) {
				addCandidate(candidates, normalized.substring(prefix.length()));
			}
		}
		if (channel == ChatChannel.CLAN && !config.clanReplyPrefix.isBlank()) {
			String normalized = normalize(content);
			String prefix = normalize(config.clanReplyPrefix);
			if (normalized.startsWith(prefix)) {
				addCandidate(candidates, normalized.substring(prefix.length()));
			}
		}

		addTextAfterLastSeparator(candidates, displayed, ": ");
		addTextAfterLastSeparator(candidates, displayed, "» ");
		addTextAfterLastSeparator(candidates, displayed, "] ");
		addTextAfterLastSeparator(candidates, displayed, "→ ");
		return new ArrayList<>(candidates);
	}

	private static void addCandidate(Set<String> candidates, String text) {
		String normalized = normalize(text);
		if (!normalized.isEmpty()) {
			candidates.add(normalized);
		}
	}

	private static void addTextAfterLastSeparator(Set<String> candidates, String text, String separator) {
		int index = text.lastIndexOf(separator);
		if (index >= 0 && index + separator.length() < text.length()) {
			addCandidate(candidates, text.substring(index + separator.length()));
		}
	}

	static boolean wildcardMatches(String wildcard, String text) {
		String normalizedWildcard = normalize(wildcard);
		String normalizedText = normalize(text);
		StringBuilder regex = new StringBuilder("^");
		int start = 0;
		for (int index = 0; index < normalizedWildcard.length(); index++) {
			if (normalizedWildcard.charAt(index) == '*') {
				regex.append(Pattern.quote(normalizedWildcard.substring(start, index))).append(".*");
				start = index + 1;
			}
		}
		regex.append(Pattern.quote(normalizedWildcard.substring(start))).append('$');
		return normalizedText.matches(regex.toString());
	}

	private boolean isRecentOwnMessage(String content, String displayed, long now) {
		if (now - lastSentAt >= OWN_MESSAGE_WINDOW_MS || lastSentText.isEmpty()) {
			return false;
		}

		String normalizedContent = normalize(content);
		String normalizedDisplayed = normalize(displayed);
		String normalizedSent = normalize(lastSentText);
		String strippedSent = stripKnownPrefix(normalizedSent);
		return normalizedContent.equals(normalizedSent)
				|| normalizedDisplayed.endsWith(normalizedSent)
				|| normalizedContent.equals(strippedSent)
				|| normalizedDisplayed.endsWith(strippedSent);
	}

	private String stripKnownPrefix(String text) {
		String globalPrefix = normalize(config.globalPrefix);
		if (!globalPrefix.isEmpty() && text.startsWith(globalPrefix)) {
			return text.substring(globalPrefix.length()).trim();
		}
		String clanPrefix = normalize(config.clanReplyPrefix);
		if (!clanPrefix.isEmpty() && text.startsWith(clanPrefix)) {
			return text.substring(clanPrefix.length()).trim();
		}
		int commandSpace = text.indexOf(' ');
		if (text.startsWith("/") && commandSpace >= 0) {
			return text.substring(commandSpace + 1).trim();
		}
		return text;
	}

	private void sendReply(String response, ChatChannel channel) {
		String outgoing = response.trim();
		if (channel == ChatChannel.GLOBAL) {
			if (config.globalPrefix.isBlank()) {
				return;
			}
			if (!outgoing.startsWith(config.globalPrefix)) {
				outgoing = config.globalPrefix + outgoing;
			}
		} else if (channel == ChatChannel.CLAN) {
			if (config.clanReplyPrefix.isBlank()) {
				return;
			}
			if (!outgoing.startsWith(config.clanReplyPrefix)) {
				outgoing = config.clanReplyPrefix.trim() + " " + outgoing;
			}
		} else if (channel == ChatChannel.PRIVATE && !outgoing.startsWith("/")) {
			if (config.privateReplyCommand.isBlank()) {
				return;
			}
			outgoing = config.privateReplyCommand.trim() + " " + outgoing;
		}

		String finalOutgoing = outgoing.trim();
		if (finalOutgoing.isEmpty()) {
			return;
		}

		recordOutgoing(finalOutgoing);
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.getConnection() == null) {
				return;
			}
			if (finalOutgoing.startsWith("/")) {
				minecraft.getConnection().sendCommand(finalOutgoing.substring(1));
			} else {
				minecraft.getConnection().sendChat(finalOutgoing);
			}
		});
	}

	public void recordOutgoing(String message) {
		lastSentText = message;
		lastSentAt = System.currentTimeMillis();
	}

	private static boolean isLikelyOwnDisplayedMessage(String displayed) {
		Minecraft minecraft = Minecraft.getInstance();
		String ownName = minecraft.getUser().getName().toLowerCase(Locale.ROOT);
		String normalized = displayed.toLowerCase(Locale.ROOT);

		int chevron = normalized.indexOf('»');
		if (chevron >= 0 && endsWithPlayerName(normalized.substring(0, chevron), ownName)) {
			return true;
		}

		if (normalized.contains("<" + ownName + ">")
				|| normalized.contains("〈" + ownName + "〉")
				|| normalized.contains("‹" + ownName + "›")) {
			return true;
		}

		int timestampEnd = normalized.lastIndexOf(']');
		int colon = normalized.indexOf(':', timestampEnd + 1);
		return colon >= 0 && endsWithPlayerName(normalized.substring(timestampEnd + 1, colon), ownName);
	}

	private static boolean endsWithPlayerName(String prefix, String playerName) {
		String trimmed = prefix.trim();
		if (!trimmed.endsWith(playerName)) {
			return false;
		}
		int start = trimmed.length() - playerName.length();
		return start == 0 || !Character.isLetterOrDigit(trimmed.charAt(start - 1))
				&& trimmed.charAt(start - 1) != '_';
	}

	private static String normalize(String text) {
		return ChatTextNormalizer.normalizeForMatching(text);
	}
}
