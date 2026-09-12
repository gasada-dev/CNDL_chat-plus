package ru.gasada.cndlchatplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class RuntimeResetCoordinator {
	private final List<Runnable> resetActions = new ArrayList<>();

	void register(Runnable resetAction) {
		resetActions.add(Objects.requireNonNull(resetAction, "resetAction"));
	}

	void resetAll() {
		for (Runnable resetAction : List.copyOf(resetActions)) {
			resetAction.run();
		}
	}
}
