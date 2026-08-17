package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

final class TemplatePeriodicMessageSchedulerTest {
	@Test
	void templateSwitchDropsOldTimerAndStartsFullNewInterval() {
		AtomicLong now = new AtomicLong();
		TemplateSwitchCoordinator coordinator = new TemplateSwitchCoordinator();
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(coordinator);
		runtime.switchTo(template("one", "old"));
		PeriodicMessageScheduler scheduler = new PeriodicMessageScheduler(runtime, null, now::get);
		coordinator.register(scheduler::resetRuntimeState);
		List<String> sent = new ArrayList<>();

		scheduler.tick(() -> true, sent::add);
		now.set(59_000);
		runtime.switchTo(template("two", "new"));
		scheduler.tick(() -> true, sent::add);
		now.set(60_000);
		scheduler.tick(() -> true, sent::add);
		assertTrue(sent.isEmpty());
		now.set(119_000);
		scheduler.tick(() -> true, sent::add);
		assertEquals(List.of("new"), sent);
	}

	@Test
	void schedulerNeverProcessesMoreThanSharedMaximum() {
		AtomicLong now = new AtomicLong();
		ServerTemplate template = ServerTemplate.empty("many", "Many");
		for (int index = 1; index <= 4; index++) {
			template.periodicMessages.add(new PeriodicMessageConfig(true, "m" + index, 1));
		}
		ServerTemplateRuntime runtime = new ServerTemplateRuntime(new TemplateSwitchCoordinator());
		runtime.switchTo(template);
		PeriodicMessageScheduler scheduler = new PeriodicMessageScheduler(runtime, null, now::get);
		List<String> sent = new ArrayList<>();
		scheduler.tick(() -> true, sent::add);
		now.set(60_000);
		scheduler.tick(() -> true, sent::add);
		assertEquals(List.of("m1", "m2", "m3"), sent);
		assertEquals(3, PeriodicMessageConfig.MAX_PERIODIC_MESSAGES);
	}

	private static ServerTemplate template(String id, String message) {
		ServerTemplate template = ServerTemplate.empty(id, id);
		template.periodicMessages.add(new PeriodicMessageConfig(true, message, 1));
		return template;
	}
}
