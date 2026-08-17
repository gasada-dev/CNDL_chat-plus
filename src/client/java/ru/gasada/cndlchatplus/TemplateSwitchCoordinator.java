package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TemplateSwitchCoordinator {
	private final List<Runnable> resetActions = new ArrayList<>();

	public void register(Runnable resetAction) {
		resetActions.add(Objects.requireNonNull(resetAction, "resetAction"));
	}

	public void resetAll() {
		for (Runnable resetAction : List.copyOf(resetActions)) {
			resetAction.run();
		}
	}
}
