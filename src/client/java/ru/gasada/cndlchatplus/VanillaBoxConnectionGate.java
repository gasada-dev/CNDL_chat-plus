package ru.gasada.cndlchatplus;

public final class VanillaBoxConnectionGate {
	private static final State INACTIVE = new State(false, null);
	private volatile State state = INACTIVE;

	public static Decision decide(String address) {
		ServerAddressNormalizer.NormalizationResult normalized = ServerAddressNormalizer.normalize(address);
		if (!normalized.valid()) return Decision.denied();
		return new Decision(true, normalized.normalizedAddress());
	}

	public State join(String address) {
		Decision decision = decide(address);
		state = decision.allowed() ? new State(true, decision.normalizedAddress()) : INACTIVE;
		return state;
	}

	public void disconnect() {
		state = INACTIVE;
	}

	public boolean active() {
		return state.active();
	}

	public State state() {
		return state;
	}

	public record Decision(boolean allowed, String normalizedAddress) {
		private static Decision denied() {
			return new Decision(false, null);
		}
	}

	public record State(boolean active, String normalizedAddress) { }
}
