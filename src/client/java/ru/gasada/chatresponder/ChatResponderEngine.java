package ru.gasada.chatresponder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;

public final class ChatResponderEngine {
	private static final long DUPLICATE_WINDOW_MS = 400;
	private static final long OWN_MESSAGE_WINDOW_MS = 5_000;

	private final ResponderConfig config;
	private final WildcardMatcher wildcardMatcher = new WildcardMatcher();
	private final OutgoingChatService outgoingChatService;
	private ServerTemplateRuntime templateRuntime;
	private String lastIncomingFingerprint = "";
	private long lastIncomingAt;
	private String lastSentText = "";
	private long lastSentAt;

	public ChatResponderEngine(ResponderConfig config) {
		this.config = config;
		this.outgoingChatService = OutgoingChatService.forMinecraft(this::recordOutgoing);
		this.templateRuntime = ServerTemplateRuntime.fromLegacyConfig(config);
	}

	OutgoingChatService outgoingChatService() {
		return outgoingChatService;
	}

	public void setTemplateRuntime(ServerTemplateRuntime templateRuntime) {
		this.templateRuntime = templateRuntime;
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
		ReplyRule rule = findFirstMatchingRuleCached(config.rules, detectedChannel, candidates);
		if (rule != null) {
			ChatChannel replyChannel = rule.channel == ChatChannel.AUTO ? detectedChannel : rule.channel;
			sendReply(rule.response, replyChannel);
		}
	}

	static ReplyRule findFirstMatchingRule(List<ReplyRule> rules, ChatChannel detectedChannel,
			List<String> candidates) {
		return findFirstMatchingRule(rules, detectedChannel, candidates, new WildcardMatcher());
	}

	private ReplyRule findFirstMatchingRuleCached(List<ReplyRule> rules, ChatChannel detectedChannel,
			List<String> candidates) {
		return findFirstMatchingRule(rules, detectedChannel, candidates, wildcardMatcher);
	}

	private static ReplyRule findFirstMatchingRule(List<ReplyRule> rules, ChatChannel detectedChannel,
			List<String> candidates, WildcardMatcher wildcardMatcher) {
		for (ReplyRule rule : rules) {
			if (!rule.enabled || rule.trigger.isBlank() || rule.response.isBlank()) {
				continue;
			}
			if (rule.channel != ChatChannel.AUTO && rule.channel != detectedChannel) {
				continue;
			}

			CompiledWildcard wildcard = wildcardMatcher.compile(rule.trigger, WildcardMatchMode.FULL_MATCH);
			if (candidates.stream().anyMatch(wildcard::matches)) {
				return rule;
			}
		}
		return null;
	}

	ChatChannel detectChannel(String content, String displayed) {
		ActiveTemplateSnapshot template = activeTemplate();
		CompiledParserSettings parsers = activeParsers();
		return template == null || parsers == null
				? ChatChannel.LOCAL
				: new ChatChannelDetector(template, parsers).detect(content, displayed);
	}

	private List<String> buildCandidates(String content, String displayed, ChatChannel channel) {
		ActiveTemplateSnapshot template = activeTemplate();
		CompiledParserSettings parsers = activeParsers();
		Set<String> candidates = new LinkedHashSet<>();
		addCandidate(candidates, content);
		addCandidate(candidates, displayed);

		if (template != null && channel == ChatChannel.GLOBAL && !template.globalPrefix().isBlank()) {
			String normalized = normalize(content);
			String prefix = normalize(template.globalPrefix());
			if (normalized.startsWith(prefix)) {
				addCandidate(candidates, normalized.substring(prefix.length()));
			}
		}
		if (template != null && channel == ChatChannel.CLAN && !template.clanReplyPrefix().isBlank()) {
			String normalized = normalize(content);
			String prefix = normalize(template.clanReplyPrefix());
			if (normalized.startsWith(prefix)) {
				addCandidate(candidates, normalized.substring(prefix.length()));
			}
		}

		if (parsers != null) {
			for (String separator : parsers.replyCandidateSeparators()) {
				addTextAfterLastSeparator(candidates, displayed, separator);
			}
		}
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
		return new WildcardMatcher().matches(wildcard, text, WildcardMatchMode.FULL_MATCH);
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
		ActiveTemplateSnapshot template = activeTemplate();
		if (template == null) {
			return text;
		}
		String globalPrefix = normalize(template.globalPrefix());
		if (!globalPrefix.isEmpty() && text.startsWith(globalPrefix)) {
			return text.substring(globalPrefix.length()).trim();
		}
		String clanPrefix = normalize(template.clanReplyPrefix());
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
		ActiveTemplateSnapshot template = activeTemplate();
		if (template == null) {
			return;
		}
		String outgoing = response.trim();
		if (channel == ChatChannel.GLOBAL) {
			if (template.globalPrefix().isBlank()) {
				return;
			}
			if (!outgoing.startsWith(template.globalPrefix())) {
				outgoing = template.globalPrefix() + outgoing;
			}
		} else if (channel == ChatChannel.CLAN) {
			if (template.clanReplyPrefix().isBlank()) {
				return;
			}
			if (!outgoing.startsWith(template.clanReplyPrefix())) {
				outgoing = template.clanReplyPrefix().trim() + " " + outgoing;
			}
		} else if (channel == ChatChannel.PRIVATE && !outgoing.startsWith("/")) {
			if (template.privateReplyCommand().isBlank()) {
				return;
			}
			outgoing = template.privateReplyCommand().trim() + " " + outgoing;
		}

		String finalOutgoing = outgoing.trim();
		if (finalOutgoing.isEmpty()) {
			return;
		}

		if (finalOutgoing.startsWith("/")) {
			outgoingChatService.sendCommand(finalOutgoing.substring(1));
		} else {
			outgoingChatService.sendChat(finalOutgoing);
		}
	}

	public void recordOutgoing(String message) {
		lastSentText = message;
		lastSentAt = System.currentTimeMillis();
	}

	public void resetRuntimeState() {
		lastIncomingFingerprint = "";
		lastIncomingAt = 0L;
		lastSentText = "";
		lastSentAt = 0L;
		wildcardMatcher.clear();
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

	private ActiveTemplateSnapshot activeTemplate() {
		return templateRuntime.activeSnapshot().orElse(null);
	}

	private CompiledParserSettings activeParsers() {
		return templateRuntime.compiledParsers().orElse(null);
	}
}
