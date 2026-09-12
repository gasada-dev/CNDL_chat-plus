package ru.gasada.cndlchatplus;

public final class VanillaBoxConnectionGate {
	private static final String DOMAIN = "vanilla-box.ru";
	private static final State INACTIVE = new State(false, null);
	private volatile State state = INACTIVE;

	public static Decision decide(String address) {
		if (address == null || address.isBlank() || !address.equals(address.trim())) {
			return Decision.denied();
		}
		int firstColon = address.indexOf(':');
		if (firstColon != address.lastIndexOf(':') || address.startsWith("[")) {
			return Decision.denied();
		}

		String host = firstColon < 0 ? address : address.substring(0, firstColon);
		if (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
			if (host.endsWith(".")) return Decision.denied();
		}
		if (host.isEmpty()) return Decision.denied();

		int port = ServerAddressNormalizer.DEFAULT_PORT;
		if (firstColon >= 0) {
			String portText = address.substring(firstColon + 1);
			if (portText.isEmpty()) return Decision.denied();
			port = 0;
			for (int index = 0; index < portText.length(); index++) {
				char character = portText.charAt(index);
				if (character < '0' || character > '9') return Decision.denied();
				port = port * 10 + character - '0';
				if (port > 65535) return Decision.denied();
			}
			if (port == 0) return Decision.denied();
		}

		ServerAddressNormalizer.NormalizationResult normalized =
				ServerAddressNormalizer.normalize(host + ":" + port);
		if (!normalized.valid()) return Decision.denied();
		String normalizedHost = normalized.normalizedAddress().substring(
				0, normalized.normalizedAddress().lastIndexOf(':'));
		if (!normalizedHost.equals(DOMAIN) && !normalizedHost.endsWith("." + DOMAIN)) {
			return Decision.denied();
		}
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
