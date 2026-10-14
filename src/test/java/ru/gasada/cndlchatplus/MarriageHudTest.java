package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

final class MarriageHudTest {
	@Test
	void disablingImmediatelyHidesUiAndBlocksOpenMenuActionsUntilReenabled() throws ReflectiveOperationException {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands = ServerCommandSettings.vanillaBoxDefaults();
		runtime.activate(config);
		PlayerInfoService playerInfo = new PlayerInfoService(runtime, (requestId, player) ->
				CompletableFuture.completedFuture(new VnbxPlayerRelationsResult(true, "Self_1", false,
						new VnbxPlayerRelationsResult.Clan(false, null, null, null), true,
						new VnbxPlayerRelationsResult.Marriage(true, null, "Partner_1"))),
				() -> true, null, Runnable::run);
		List<String> sent = new ArrayList<>();
		OutgoingChatService.Transport transport = new OutgoingChatService.Transport() {
			@Override public boolean connected() { return true; }
			@Override public void execute(Runnable action) { action.run(); }
			@Override public void sendChat(String message) { }
			@Override public void sendCommand(String command) { sent.add(command); }
		};
		ServerCommandService commands = new ServerCommandService(runtime,
				new OutgoingChatService(transport, ignored -> { }));
		AtomicBoolean enabled = new AtomicBoolean(true);
		MarriageHud hud = new MarriageHud(runtime, playerInfo, commands, null, enabled::get);
		var controllerField = MarriageHud.class.getDeclaredField("controller");
		controllerField.setAccessible(true);
		MarriageHudController controller = (MarriageHudController) controllerField.get(hud);
		controller.tick(new Object(), runtime.activeSnapshot().orElseThrow().generation(), true, () -> "Self_1");
		MarriageHudSnapshot snapshot = controller.snapshot();
		MarriageHudSnapshot.Context openMenuContext = snapshot.context();

		assertTrue(hud.visible(snapshot));
		assertTrue(hud.execute(openMenuContext, MarriageAction.KISS).success());
		enabled.set(false);
		assertFalse(hud.visible(snapshot));
		CndlChatPlusClient.CONNECTION_GATE.join("example.org");
		try {
			assertFalse(hud.rightClick(0, 0, 800, 600, null, null));
		} finally {
			CndlChatPlusClient.CONNECTION_GATE.disconnect();
		}
		for (MarriageAction action : MarriageAction.values()) {
			assertFalse(hud.execute(openMenuContext, action).success());
		}
		assertEquals(List.of("marry kiss"), sent);
		assertTrue(commands.marryHome().success());
		assertTrue(controller.snapshot().visible());
		enabled.set(true);
		assertTrue(hud.visible(snapshot));
		assertTrue(hud.execute(openMenuContext, MarriageAction.TP).success());
		assertEquals(List.of("marry kiss", "marry home", "marry tp"), sent);
	}

	@Test
	void marksOfflinePartnerInGrayAndLeavesOnlinePartnerUnchanged() {
		MarriageHud.PartnerStatus offline = MarriageHud.partnerStatus("Partner_1", Set.of("Other_1"));
		MarriageHud.PartnerStatus online = MarriageHud.partnerStatus("Partner_1", Set.of("partner_1"));

		assertEquals("оффлайн", offline.text());
		assertEquals(ThemeTokens.textMuted(), offline.color());
		assertEquals("", online.text());
	}
}
