package ru.gasada.cndlchatplus;

import java.util.Objects;
import java.util.function.Supplier;

final class MarriageHudController {
	private final PlayerInfoService playerInfo;
	private volatile MarriageHudSnapshot snapshot = MarriageHudSnapshot.empty();
	private Object connectionIdentity;
	private long generation = -1L;
	private String partner;
	private boolean requested;
	private boolean bridgeAvailable;
	private long stateEpoch;

	MarriageHudController(PlayerInfoService playerInfo) {
		this.playerInfo = playerInfo;
	}

	void tick(Object connection, long currentGeneration, boolean bridgeAvailable,
			Supplier<String> usernameSupplier) {
		if (connection == null || currentGeneration < 0L) {
			resetRuntimeState();
			return;
		}
		if (connectionIdentity != connection || generation != currentGeneration) {
			stateEpoch++;
			connectionIdentity = connection;
			generation = currentGeneration;
			partner = null;
			requested = false;
			this.bridgeAvailable = bridgeAvailable;
			snapshot = MarriageHudSnapshot.empty();
		}
		if (!bridgeAvailable) {
			if (this.bridgeAvailable) {
				stateEpoch++;
				partner = null;
				requested = false;
				snapshot = MarriageHudSnapshot.empty();
			}
			this.bridgeAvailable = false;
			return;
		}
		this.bridgeAvailable = true;
		if (requested) return;

		requested = true;
		String username = usernameSupplier.get();
		if (!PlayerNameValidator.validate(username).valid()) return;
		Object requestConnection = connectionIdentity;
		long requestGeneration = generation;
		long requestEpoch = stateEpoch;
		playerInfo.refresh(username).whenComplete((result, error) -> {
			if (connectionIdentity != requestConnection || generation != requestGeneration
					|| stateEpoch != requestEpoch || !this.bridgeAvailable) return;
			partner = error == null ? validPartner(result) : null;
			publish();
		});
	}

	MarriageHudSnapshot snapshot() {
		return snapshot;
	}

	void resetRuntimeState() {
		stateEpoch++;
		connectionIdentity = null;
		generation = -1L;
		partner = null;
		requested = false;
		bridgeAvailable = false;
		snapshot = MarriageHudSnapshot.empty();
	}

	ActionResult execute(MarriageHudSnapshot.Context context, MarriageAction action,
			ServerCommandService commands) {
		if (!current(context)) return ActionResult.failure("Данные о браке устарели");
		ServerCommandService.CommandResult result = switch (action) {
			case KISS -> commands.marryKiss();
			case HOME -> commands.marryHome();
			case TP -> commands.marryTp();
		};
		return result.success() ? ActionResult.ok() : ActionResult.failure(result.errorMessage());
	}

	private boolean current(MarriageHudSnapshot.Context context) {
		return context != null && snapshot.visible()
				&& connectionIdentity == context.connectionIdentity()
				&& generation == context.generation()
				&& Objects.equals(partner, context.partner());
	}

	private void publish() {
		snapshot = partner != null
				? new MarriageHudSnapshot(new MarriageHudSnapshot.Context(connectionIdentity, generation, partner))
				: MarriageHudSnapshot.empty();
	}

	private static String validPartner(PlayerInfoService.LoadResult result) {
		if (result == null || !result.success() || result.profile() == null) return null;
		PlayerInfoProfile.Marriage marriage = result.profile().marriage();
		if (marriage == null || !marriage.married()) return null;
		String candidate = marriage.partner();
		return PlayerNameValidator.validate(candidate).valid() ? candidate : null;
	}

	record ActionResult(boolean success, String errorMessage) {
		private static ActionResult ok() {
			return new ActionResult(true, "");
		}

		private static ActionResult failure(String message) {
			return new ActionResult(false, message);
		}
	}
}
