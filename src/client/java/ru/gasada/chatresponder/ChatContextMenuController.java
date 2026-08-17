package ru.gasada.chatresponder;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ChatContextMenuController {
	private final ChatContextMenu menu = new ChatContextMenu();
	private final ChatMessageSenderExtractor senderExtractor = new ChatMessageSenderExtractor();
	private final ContextMenuBuilder menuBuilder = new ContextMenuBuilder();

	public boolean open() {
		return menu.open();
	}

	public void render(CompatGraphics graphics, int mouseX, int mouseY, Minecraft minecraft) {
		menu.render(graphics, minecraft.font, mouseX, mouseY);
	}

	public boolean rightClick(double mouseX, double mouseY, int screenWidth, int screenHeight,
			Minecraft minecraft) {
		if (!Boolean.TRUE.equals(GasadaChatResponderClient.CONFIG.chatContextMenuEnabled)) return false;
		if (menu.open()) {
			menu.close();
			return true;
		}
		ChatMessageTarget target = ((ChatMessageUnderMouseAccess) ChatAccess.chat(minecraft))
				.gasada$messageUnderMouse(mouseX, mouseY, screenHeight);
		if (target == null) return false;
		CompiledParserSettings parsers = GasadaChatResponderClient.TEMPLATE_RUNTIME == null ? null
				: GasadaChatResponderClient.TEMPLATE_RUNTIME.compiledParsers().orElse(null);
		String text = ChatMessageTextSanitizer.stripSyntheticLabels(target.component().getString());
		boolean system = GasadaChatResponderClient.CHAT_TABS != null
				&& GasadaChatResponderClient.CHAT_TABS.classify(text, target.fromGame()) == ChatTab.SYSTEM;
		ChatMessageSenderExtractor.Sender sender = system ? null
				: senderExtractor.extract(text, parsers).orElse(null);
		menu.open(text, sender,
				menuBuilder.build(sender, capabilities()), minecraft.font,
				(int) mouseX, (int) mouseY, screenWidth, screenHeight);
		return true;
	}

	public boolean leftClick(double mouseX, double mouseY, Screen screen, Consumer<String> draftConsumer,
			Minecraft minecraft) {
		if (!menu.open()) return false;
		ChatContextAction action = menu.actionAt(mouseX, mouseY);
		if (action != null) execute(action, screen, draftConsumer, minecraft);
		menu.close();
		return true;
	}

	public boolean close() {
		if (!menu.open()) return false;
		menu.close();
		return true;
	}

	private ContextMenuBuilder.Capabilities capabilities() {
		ServerTemplateRuntime runtime = GasadaChatResponderClient.TEMPLATE_RUNTIME;
		ServerCommandService commands = GasadaChatResponderClient.SERVER_COMMANDS;
		boolean active = runtime != null && runtime.activeSnapshot().isPresent() && commands != null;
		if (!active) return new ContextMenuBuilder.Capabilities(false, false, false, false, false, false);
		return new ContextMenuBuilder.Capabilities(true,
				commands.supportsDraft(CommandTemplateValidator.CommandType.PRIVATE_MESSAGE),
				commands.supports(CommandTemplateValidator.CommandType.IGNORE_PLAYER),
				commands.supportsDraft(CommandTemplateValidator.CommandType.PAY),
				commands.supports(CommandTemplateValidator.CommandType.CALL),
				commands.supportsDraft(CommandTemplateValidator.CommandType.MAIL));
	}

	private void execute(ChatContextAction action, Screen screen, Consumer<String> draftConsumer,
			Minecraft minecraft) {
		String player = menu.sender() == null ? null : menu.sender().name();
		ServerCommandService commands = GasadaChatResponderClient.SERVER_COMMANDS;
		switch (action) {
			case COPY_MESSAGE -> minecraft.keyboardHandler.setClipboard(menu.message());
			case COPY_NICK -> minecraft.keyboardHandler.setClipboard(player);
			case PRIVATE_MESSAGE -> commands.privateMessageDraft(player).ifPresent(draftConsumer);
			case ADD_FRIEND -> GasadaChatResponderClient.FRIEND_ACTIONS.addFriend(player);
			case IGNORE -> commands.ignorePlayer(player);
			case PLAYER_INFO -> ClientUi.setScreen(minecraft, new PlayerInfoScreen(screen, player));
			case PAY -> commands.payDraft(player).ifPresent(draftConsumer);
			case CALL -> commands.call(player);
			case MAIL -> commands.mailDraft(player).ifPresent(draftConsumer);
		}
	}
}
