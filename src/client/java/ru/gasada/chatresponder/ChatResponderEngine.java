package ru.gasada.chatresponder;

import java.util.List;
import java.util.function.LongSupplier;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;

public final class ChatResponderEngine {
	private static final long DUPLICATE_WINDOW_MS = 400;
	private static final long OWN_MESSAGE_WINDOW_MS = 5_000;

	private final OutgoingChatService outgoingChatService;
	private final DuplicateMessageGuard duplicateGuard;
	private final OwnMessageGuard ownMessageGuard;
	private final ReplyCandidateBuilder candidateBuilder = new ReplyCandidateBuilder();
	private ServerTemplateRuntime templateRuntime;

	public ChatResponderEngine(ResponderConfig config) {
		this(config, System::currentTimeMillis);
	}

	ChatResponderEngine(ResponderConfig config, LongSupplier clock) {
		this.duplicateGuard = new DuplicateMessageGuard(DUPLICATE_WINDOW_MS, clock);
		this.ownMessageGuard = new OwnMessageGuard(OWN_MESSAGE_WINDOW_MS, clock);
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
		ActiveTemplateSnapshot template = activeTemplate();
		CompiledParserSettings parsers = activeParsers();
		ReplyRuleMatcher ruleMatcher = templateRuntime.compiledReplyRules().orElse(null);
		if (template == null || parsers == null || ruleMatcher == null
				|| !template.responderEnabled() || content == null || displayed == null) {
			return;
		}
		if (OwnMessageGuard.isLikelyOwnDisplayedMessage(displayed,
				Minecraft.getInstance().getUser().getName())) {
			return;
		}
		if (duplicateGuard.isDuplicate(content, displayed)) {
			return;
		}
		if (ownMessageGuard.isRecentEcho(content, displayed, template)) {
			return;
		}

		ChatChannel detectedChannel = new ChatChannelDetector(template, parsers).detect(content, displayed);
		List<String> candidates = candidateBuilder.build(content, displayed, detectedChannel, template, parsers);
		ActiveTemplateSnapshot.RuleSnapshot rule = ruleMatcher.findFirst(detectedChannel, candidates);
		if (rule != null) {
			ChatChannel replyChannel = rule.channel() == ChatChannel.AUTO ? detectedChannel : rule.channel();
			sendReply(rule.response(), replyChannel);
		}
	}

	static ReplyRule findFirstMatchingRule(List<ReplyRule> rules, ChatChannel detectedChannel,
			List<String> candidates) {
		List<ActiveTemplateSnapshot.RuleSnapshot> snapshots = rules.stream()
				.map(rule -> new ActiveTemplateSnapshot.RuleSnapshot(
						rule.enabled, rule.trigger, rule.response, rule.channel))
				.toList();
		ActiveTemplateSnapshot.RuleSnapshot matched = ReplyRuleMatcher.compile(snapshots)
				.findFirst(detectedChannel, candidates);
		return matched == null ? null : rules.get(snapshots.indexOf(matched));
	}

	ChatChannel detectChannel(String content, String displayed) {
		ActiveTemplateSnapshot template = activeTemplate();
		CompiledParserSettings parsers = activeParsers();
		return template == null || parsers == null
				? ChatChannel.LOCAL
				: new ChatChannelDetector(template, parsers).detect(content, displayed);
	}

	static boolean wildcardMatches(String wildcard, String text) {
		return new WildcardMatcher().matches(wildcard, text, WildcardMatchMode.FULL_MATCH);
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
		ownMessageGuard.recordOutgoing(message);
	}

	public void resetRuntimeState() {
		duplicateGuard.reset();
		ownMessageGuard.reset();
	}

	private ActiveTemplateSnapshot activeTemplate() {
		return templateRuntime.activeSnapshot().orElse(null);
	}

	private CompiledParserSettings activeParsers() {
		return templateRuntime.compiledParsers().orElse(null);
	}
}
