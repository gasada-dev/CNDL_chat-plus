package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

final class MarriageLookupManagerTest {
	@Test
	void scansPagesAndFindsPartnerAroundHeart() {
		Fixture fixture = new Fixture();
		AtomicReference<MarriageLookupManager.Result> result = new AtomicReference<>();

		assertTrue(fixture.manager.queueLookup("Target_1", result::set));
		fixture.manager.tick(true);
		assertEquals(List.of(1), fixture.sentPages);
		assertFalse(fixture.manager.handleMessage("Список замужних игроков - страница 1/2"));
		assertFalse(fixture.manager.handleMessage("Other_1 ❤ Other_2"));
		assertFalse(fixture.manager.handleMessage("Страница 1/2"));
		fixture.now[0] += 500;
		fixture.manager.tick(true);
		assertEquals(List.of(1, 2), fixture.sentPages);
		assertFalse(fixture.manager.handleMessage("Список замужних игроков - страница 2/2"));
		assertFalse(fixture.manager.handleMessage("Target_1 ♥ Partner_2"));

		assertEquals("Partner_2", result.get().partner());
		assertTrue(result.get().completed());
	}

	@Test
	void reportsExhaustiveNotFoundAndDoesNotHideUnrelatedChat() {
		Fixture fixture = new Fixture();
		AtomicReference<MarriageLookupManager.Result> result = new AtomicReference<>();
		fixture.manager.queueLookup("Target", result::set);
		fixture.manager.tick(true);

		assertTrue(fixture.manager.handleMessage("Обычное сообщение игрока"));
		assertFalse(fixture.manager.handleMessage("Список замужних игроков - страница 1/1"));
		assertFalse(fixture.manager.handleMessage("Other ❤ Partner"));
		assertFalse(fixture.manager.handleMessage("Страница 1/1"));
		assertTrue(result.get().completed());
		assertNull(result.get().partner());
	}

	@Test
	void waitsForSharedCoordinatorAndCancelsOnDisconnect() {
		Fixture fixture = new Fixture();
		Object blocker = new Object();
		assertTrue(fixture.coordinator.tryAcquire(blocker));
		AtomicReference<MarriageLookupManager.Result> result = new AtomicReference<>();
		fixture.manager.queueLookup("Target", result::set);
		fixture.manager.tick(true);
		assertTrue(fixture.sentPages.isEmpty());

		fixture.coordinator.release(blocker);
		fixture.manager.tick(true);
		assertEquals(List.of(1), fixture.sentPages);
		fixture.manager.tick(false);
		assertFalse(result.get().completed());
	}

	@Test
	void recognizesConfiguredEmptyListMessage() {
		Fixture fixture = new Fixture();
		AtomicReference<MarriageLookupManager.Result> result = new AtomicReference<>();
		fixture.manager.queueLookup("Target", result::set);
		fixture.manager.tick(true);
		assertFalse(fixture.manager.handleMessage("Нет женатых игроков."));
		assertTrue(result.get().completed());
		assertNull(result.get().partner());
	}

	@Test
	void rejectsVanillaBoxEvenWithMarriageSettings() {
		ServerTemplate template = ServerTemplate.empty("vanilla-box", "Vanilla-box");
		template.commands.marriageList = "marry list {page}";
		ParserSettings.applyVanillaGameMarriageDefaults(template.parsers);
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(template);
		MarriageLookupManager manager = new MarriageLookupManager(runtime, ignored -> true,
				new ServerLookupCoordinator(), System::currentTimeMillis);

		assertFalse(manager.queueLookup("Target", ignored -> { }));
	}

	private static final class Fixture {
		private final long[] now = {1_000};
		private final List<Integer> sentPages = new ArrayList<>();
		private final ServerLookupCoordinator coordinator = new ServerLookupCoordinator();
		private final MarriageLookupManager manager;

		private Fixture() {
			ServerTemplate template = ServerTemplate.empty("vanilla-game", "Vanilla-game");
			template.commands.marriageList = "marry list {page}";
			ParserSettings.applyVanillaGameMarriageDefaults(template.parsers);
			ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
			runtime.switchTo(template);
			manager = new MarriageLookupManager(runtime, page -> {
				sentPages.add(page);
				return true;
			}, coordinator, () -> now[0]);
		}
	}
}
