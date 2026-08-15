package ru.gasada.chatresponder;

public final class PlayerInfoSettings {
	public PlayerInfoProvider provider = PlayerInfoProvider.NONE;
	public boolean providerConfigured;
	public boolean marriageLookupConfigured;

	public PlayerInfoSettings copy() {
		PlayerInfoSettings copy = new PlayerInfoSettings();
		copy.provider = provider == null ? PlayerInfoProvider.NONE : provider;
		copy.providerConfigured = providerConfigured;
		copy.marriageLookupConfigured = marriageLookupConfigured;
		return copy;
	}
}
