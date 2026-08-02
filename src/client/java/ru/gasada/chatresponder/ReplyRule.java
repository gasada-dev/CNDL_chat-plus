package ru.gasada.chatresponder;

public final class ReplyRule {
	public boolean enabled = true;
	public String trigger = "";
	public String response = "";
	public ChatChannel channel = ChatChannel.AUTO;

	public ReplyRule() {
	}

	public ReplyRule(String trigger, String response, ChatChannel channel) {
		this.trigger = trigger;
		this.response = response;
		this.channel = channel;
	}
}
