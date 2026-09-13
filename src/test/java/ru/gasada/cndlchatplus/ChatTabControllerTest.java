package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class ChatTabControllerTest {
	private AtomicBoolean enabled;
	private ChatTabController controller;

	@BeforeEach
	void setUp() {
		enabled = new AtomicBoolean(true);
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(VanillaBoxConfig.fromCompatible(ResponderConfig.defaults()));
		controller = new ChatTabController(
				new ChatTabClassifier(runtime),
				enabled::get);
	}

	@Test
	void allTabShowsEverythingAndUnreadAccumulatesPerTab() {
		controller.recordMessage(Component.literal("(!) Player » продаю"), false);
		controller.recordMessage(Component.literal("[лс] Player » привет"), false);

		assertEquals(1, controller.unread(ChatTab.GLOBAL));
		assertEquals(1, controller.unread(ChatTab.PRIVATE));
		assertEquals(0, controller.unread(ChatTab.LOCAL));
	}

	@Test
	void activeTabFiltersByRecordedChannel() {
		Component global = Component.literal("(!) Player » продаю");
		Component local = Component.literal("Player » привет");
		controller.recordMessage(global, false);
		controller.recordMessage(local, false);

		controller.chatOpened();
		controller.selectTab(ChatTab.GLOBAL, null);

		assertTrue(controller.isVisible(global, null));
		assertFalse(controller.isVisible(local, null));
		assertEquals(0, controller.unread(ChatTab.GLOBAL));
		assertEquals(1, controller.unread(ChatTab.LOCAL));

		controller.recordMessage(Component.literal("(!) Player » ещё"), false);
		assertEquals(0, controller.unread(ChatTab.GLOBAL));
	}

	@Test
	void systemSourceHintClassifiesMarkerlessMessages() {
		Component system = Component.literal("Игрок зашёл на сервер");
		controller.recordMessage(system, true);

		controller.selectTab(ChatTab.SYSTEM, null);
		assertTrue(controller.isVisible(system, true));
		assertFalse(controller.isVisible(system, false));

		controller.selectTab(ChatTab.LOCAL, null);
		assertFalse(controller.isVisible(system, true));
	}

	@Test
	void restoredLocalSourceOverridesSystemGuiHint() {
		Component restored = Component.literal("Player » сохранённое сообщение");
		controller.mapRestoredSource(restored, false);

		ResponderConfig config = new ResponderConfig();
		config.customChatTabs = new ArrayList<>(List.of(
				new CustomChatTab("local", "Local", List.of(ChatTabSource.LOCAL), "")));
		controller.reloadConfig(config);
		controller.selectTab(custom(controller, "local"), null);

		assertTrue(controller.isVisible(restored, true));
	}

	@Test
	void markersBeatPacketTypeEvenWithoutRecordedSource() {
		Component global = Component.literal("(!) Player » продаю");
		controller.selectTab(ChatTab.GLOBAL, null);
		assertTrue(controller.isVisible(global, true));
		assertTrue(controller.isVisible(global, false));
	}

	@Test
	void remapComponentTransfersSourceToPrefixedInstance() {
		Component original = Component.literal("Игрок вышел");
		controller.recordMessage(original, true);

		Component prefixed = Component.literal("[12:34] ").append(original);
		controller.remapComponent(original, prefixed);

		controller.selectTab(ChatTab.SYSTEM, null);
		assertTrue(controller.isVisible(prefixed, null));
	}

	@Test
	void disabledControllerShowsEverything() {
		enabled.set(false);
		controller.selectTab(ChatTab.SYSTEM, null);
		assertTrue(controller.isVisible(Component.literal("что угодно"), false));
	}

	@Test
	void resetClearsUnreadAndSources() {
		controller.recordMessage(Component.literal("(!) Player » текст"), false);
		controller.selectTab(ChatTab.GLOBAL, null);
		controller.resetRuntimeState();

		assertEquals(0, controller.unread(ChatTab.GLOBAL));
		assertEquals(ChatTab.ALL, controller.active());
	}

	@Test
	void definitionsHideBuiltInsExceptAllAndReloadFallsBackFromRemovedSelection() {
		ResponderConfig config = ResponderConfig.defaults();
		config.hiddenBuiltInTabs = new ArrayList<>(List.of("GLOBAL", "ALL"));
		controller.reloadConfig(config);

		assertTrue(controller.definitions().stream().anyMatch(tab -> tab.builtIn() == ChatTab.ALL));
		assertFalse(controller.definitions().stream().anyMatch(tab -> tab.builtIn() == ChatTab.GLOBAL));
		controller.selectTab(ChatTab.GLOBAL, null);
		assertEquals(ChatTab.ALL, controller.active());

		ChatTabDefinition custom = custom(controller, CustomChatTab.DEFAULT_VOICE_ID);
		controller.selectTab(custom, null);
		assertEquals(custom, controller.activeDefinition());
		config.customChatTabs.clear();
		controller.reloadConfig(config);
		assertEquals(ChatTab.ALL, controller.active());
		assertEquals(ChatTab.ALL, controller.activeDefinition().builtIn());
	}

	@Test
	void customTabMatchesNormalizedCanonicalDisplayText() {
		ChatTimestamps timestamps = new ChatTimestamps(() -> true);
		ResponderConfig config = ResponderConfig.defaults();
		controller = controller(config, timestamps);
		controller.selectTab(custom(controller, CustomChatTab.DEFAULT_VOICE_ID), null);

		Component exact = Component.literal("[02:51] (Войс) gasada » 1");
		Component formatted = Component.literal("§a(ВОЙС)§r gasada » 2");
		Component stamped = timestamps.at(Component.literal("[VIP head](Войс) gasada » 3"), 0L);
		assertTrue(controller.isVisible(exact, false));
		assertTrue(controller.isVisible(formatted, false));
		assertTrue(controller.isVisible(stamped, false));
		assertFalse(controller.isVisible(Component.literal("gasada » другое"), false));
	}

	@Test
	void oneMessageIncrementsEveryMatchingSourceTabExceptOpenActiveTab() {
		ResponderConfig config = new ResponderConfig();
		config.customChatTabs = new ArrayList<>(List.of(
				new CustomChatTab("voice", "Voice", List.of(ChatTabSource.VOICE), "/gc"),
				new CustomChatTab("mixed", "Mixed",
						List.of(ChatTabSource.GLOBAL, ChatTabSource.VOICE), "")));
		controller.reloadConfig(config);
		controller.recordMessage(Component.literal("(Войс) gasada » 1"), false);

		assertEquals(1, controller.unread(custom(controller, "voice")));
		assertEquals(1, controller.unread(custom(controller, "mixed")));

		controller.chatOpened();
		controller.selectTab(custom(controller, "voice"), null);
		controller.recordMessage(Component.literal("(!) gasada » 2"), false);
		assertEquals(0, controller.unread(custom(controller, "voice")));
		assertEquals(2, controller.unread(custom(controller, "mixed")));
	}

	private ChatTabController controller(ResponderConfig config, ChatTimestamps timestamps) {
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(VanillaBoxConfig.fromCompatible(ResponderConfig.defaults()));
		return new ChatTabController(new ChatTabClassifier(runtime), enabled::get, config, timestamps);
	}

	private static ChatTabDefinition custom(ChatTabController controller, String id) {
		return controller.definitions().stream()
				.filter(tab -> tab.custom() && tab.id().equals(id))
				.findFirst().orElseThrow();
	}
}
