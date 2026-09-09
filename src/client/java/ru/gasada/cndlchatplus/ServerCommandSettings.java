package ru.gasada.cndlchatplus;

public final class ServerCommandSettings {
	public String ignorePlayer = "";
	public String lookupFriend = "";
	public String privateMessage = "";
	public String pay = "";
	public String call = "";
	public String mail = "";
	public String acceptTeleport = "";
	public String protectionAdd = "";
	public String protectionRemove = "";
	public String traderTrustedAdd = "";
	public String traderTrustedRemove = "";
	public String claimFly = "";
	public String enderChest = "";
	public String marryKiss = "";
	public String marryHome = "";
	public String marryTp = "";
	public boolean nearbyPlayerCommandsConfigured;
	public boolean traderTrustedRemoveConfigured;
	public boolean utilityCommandsConfigured;

	public static ServerCommandSettings vanillaBoxDefaults() {
		ServerCommandSettings settings = new ServerCommandSettings();
		settings.ignorePlayer = "ignoreplayer {player}";
		settings.lookupFriend = "clan lookup {player}";
		settings.privateMessage = "w {player} {message}";
		settings.pay = "pay {player} {amount}";
		settings.call = "call {player}";
		settings.mail = "mail send {player} {message}";
		settings.acceptTeleport = "tpaccept";
		settings.protectionAdd = "ps add {player}";
		settings.protectionRemove = "ps remove {player}";
		settings.traderTrustedAdd = "vm trusted add {player}";
		settings.traderTrustedRemove = "vm trusted remove {player}";
		settings.claimFly = "claimfly";
		settings.enderChest = "enderchest";
		settings.marryKiss = "marry kiss";
		settings.marryHome = "marry home";
		settings.marryTp = "marry tp";
		settings.nearbyPlayerCommandsConfigured = true;
		settings.traderTrustedRemoveConfigured = true;
		settings.utilityCommandsConfigured = true;
		return settings;
	}

	public ServerCommandSettings copy() {
		ServerCommandSettings copy = new ServerCommandSettings();
		copy.ignorePlayer = ignorePlayer;
		copy.lookupFriend = lookupFriend;
		copy.privateMessage = privateMessage;
		copy.pay = pay;
		copy.call = call;
		copy.mail = mail;
		copy.acceptTeleport = acceptTeleport;
		copy.protectionAdd = protectionAdd;
		copy.protectionRemove = protectionRemove;
		copy.traderTrustedAdd = traderTrustedAdd;
		copy.traderTrustedRemove = traderTrustedRemove;
		copy.claimFly = claimFly;
		copy.enderChest = enderChest;
		copy.marryKiss = marryKiss;
		copy.marryHome = marryHome;
		copy.marryTp = marryTp;
		copy.nearbyPlayerCommandsConfigured = nearbyPlayerCommandsConfigured;
		copy.traderTrustedRemoveConfigured = traderTrustedRemoveConfigured;
		copy.utilityCommandsConfigured = utilityCommandsConfigured;
		return copy;
	}
}
