package ru.gasada.cndlchatplus;

public final class ServerCommandSettings {
	public String ignorePlayer = "";
	public String lookupFriend = "";
	public String privateMessage = "";
	public String pay = "";
	public String call = "";
	public String mail = "";
	public String marriageList = "";
	public String acceptTeleport = "";

	public static ServerCommandSettings vanillaBoxDefaults() {
		ServerCommandSettings settings = new ServerCommandSettings();
		settings.ignorePlayer = "ignoreplayer {player}";
		settings.lookupFriend = "clan lookup {player}";
		settings.privateMessage = "w {player} {message}";
		settings.pay = "pay {player} {amount}";
		settings.call = "call {player}";
		settings.mail = "mail send {player} {message}";
		settings.acceptTeleport = "tpaccept";
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
		copy.marriageList = marriageList;
		copy.acceptTeleport = acceptTeleport;
		return copy;
	}
}
