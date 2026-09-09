package ru.gasada.cndlchatplus;

public record MarriageHudSnapshot(Context context) {
	public static MarriageHudSnapshot empty() {
		return new MarriageHudSnapshot(null);
	}

	public boolean visible() {
		return context != null;
	}

	public String partner() {
		return context == null ? null : context.partner();
	}

	public record Context(Object connectionIdentity, long generation, String partner) { }
}
