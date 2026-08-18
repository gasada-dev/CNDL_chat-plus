package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ServerCommandServiceTest {
	private FakeTransport transport;
	private ServerTemplateRuntime runtime;
	private ServerCommandService commands;

	@BeforeEach
	void setUp() {
		transport = new FakeTransport();
		OutgoingChatService outgoing = new OutgoingChatService(transport, ignored -> { });
		runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.switchTo(template);
		commands = new ServerCommandService(runtime, outgoing);
	}

	@Test
	void buildsEveryVanillaBoxCommandFromActiveTemplate() {
		assertTrue(commands.ignorePlayer("Player_1").success());
		assertTrue(commands.lookupFriend("Player_1").success());
		assertTrue(commands.privateMessage("Player_1", "hello").success());
		assertTrue(commands.pay("Player_1", "10,25").success());
		assertTrue(commands.call("Player_1").success());
		assertTrue(commands.mail("Player_1", "offline hello").success());
		assertTrue(commands.acceptTeleport().success());

		assertEquals(List.of(
				"ignoreplayer Player_1",
				"clan lookup Player_1",
				"w Player_1 hello",
				"pay Player_1 10.25",
				"call Player_1",
				"mail send Player_1 offline hello",
				"tpaccept"), transport.commands);
	}

	@Test
	void missingCommandNeverFallsBackToVanillaBox() {
		ServerTemplate empty = ServerTemplate.empty("empty", "Empty");
		empty.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.switchTo(empty);
		ServerCommandService.CommandResult result = commands.call("Player_1");
		assertFalse(result.success());
		assertTrue(result.errorMessage().contains("отсутствует"));
		assertTrue(transport.commands.isEmpty());
	}

	@Test
	void rejectsInvalidPlayerMessageAndAmountBeforeTransport() {
		assertFalse(commands.call("Игрок").success());
		assertFalse(commands.privateMessage("Player", "line\nbreak").success());
		assertFalse(commands.pay("Player", "0").success());
		assertFalse(commands.pay("Player", "1e3").success());
		assertTrue(transport.commands.isEmpty());
	}

	@Test
	void rejectsInvalidTemplateAndDisconnectedTransport() {
		ServerTemplate invalid = ServerTemplate.empty("invalid", "Invalid");
		invalid.commands.call = "call {unknown}";
		invalid.parsers = ParserSettings.vanillaBoxDefaults();
		runtime.switchTo(invalid);
		assertFalse(commands.call("Player").success());
		transport.connected = false;
		runtime.switchTo(templateWithCommands());
		assertFalse(commands.call("Player").success());
		assertTrue(transport.commands.isEmpty());
	}

	@Test
	void outgoingServiceIsSingleTransportPointForChatAndCommands() {
		List<String> recorded = new ArrayList<>();
		OutgoingChatService outgoing = new OutgoingChatService(transport, recorded::add);
		assertTrue(outgoing.sendChat("  hello  ").success());
		assertTrue(outgoing.sendCommand("say hello").success());
		assertFalse(outgoing.sendCommand("/say hidden slash").success());
		assertEquals(List.of("hello"), transport.chats);
		assertEquals(List.of("say hello"), transport.commands);
		assertEquals(List.of("hello", "/say hello"), recorded);
	}

	@Test
	void marriageListUsesActiveTemplateAndValidatedPage() {
		ServerTemplate template = templateWithCommands();
		template.commands.marriageList = "marry list {page}";
		runtime.switchTo(template);
		assertTrue(commands.marriageList(2).success());
		assertFalse(commands.marriageList(0).success());
		assertEquals(List.of("marry list 2"), transport.commands);
	}

	@Test
	void acceptsTeleportThroughConfiguredCommand() {
		ServerTemplate template = templateWithCommands();
		template.commands.acceptTeleport = "tpaccept";
		runtime.switchTo(template);

		assertTrue(commands.acceptTeleport().success());
		assertEquals(List.of("tpaccept"), transport.commands);
	}

	@Test
	void buildsValidatedDraftsWithoutSending() {
		assertEquals("/w Player_1 ", commands.privateMessageDraft("Player_1").orElseThrow());
		assertEquals("/pay Player_1 ", commands.payDraft("Player_1").orElseThrow());
		assertEquals("/mail send Player_1 ", commands.mailDraft("Player_1").orElseThrow());
		assertTrue(transport.commands.isEmpty());
	}

	@Test
	void missingOrInvalidDraftTemplateFailsClosed() {
		ServerTemplate empty = ServerTemplate.empty("empty", "Empty");
		runtime.switchTo(empty);

		assertTrue(commands.privateMessageDraft("Player_1").isEmpty());
		assertTrue(commands.payDraft("bad player").isEmpty());
		assertFalse(commands.supports(CommandTemplateValidator.CommandType.CALL));
	}

	@Test
	void draftRequiresInputPlaceholderAtEnd() {
		ServerTemplate template = templateWithCommands();
		template.commands.privateMessage = "tell {message} to {player}";
		runtime.switchTo(template);

		assertTrue(commands.privateMessageDraft("Player_1").isEmpty());
		assertFalse(commands.supportsDraft(CommandTemplateValidator.CommandType.PRIVATE_MESSAGE));
	}

	private static ServerTemplate templateWithCommands() {
		ServerTemplate template = ServerTemplate.empty("commands", "Commands");
		template.commands = ServerCommandSettings.vanillaBoxDefaults();
		template.parsers = ParserSettings.vanillaBoxDefaults();
		return template;
	}

	private static final class FakeTransport implements OutgoingChatService.Transport {
		private boolean connected = true;
		private final List<String> chats = new ArrayList<>();
		private final List<String> commands = new ArrayList<>();

		@Override
		public boolean connected() {
			return connected;
		}

		@Override
		public void execute(Runnable action) {
			action.run();
		}

		@Override
		public void sendChat(String message) {
			chats.add(message);
		}

		@Override
		public void sendCommand(String command) {
			commands.add(command);
		}
	}
}
