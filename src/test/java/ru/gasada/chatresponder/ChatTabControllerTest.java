package ru.gasada.chatresponder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		controller = new ChatTabController(
				new ChatTabClassifier(ServerTemplateRuntime.fromLegacyConfig(ResponderConfig.defaults())),
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
		controller.resetRuntimeState();

		assertEquals(0, controller.unread(ChatTab.GLOBAL));
	}
}
