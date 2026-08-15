package ru.gasada.chatresponder;

import java.util.List;

public record PlayerInfoProfile(
		String username,
		String registrationDate,
		String lastLogin,
		String about,
		String city,
		String telegram,
		String vk,
		String website,
		Clan clan,
		Marriage marriage,
		List<Building> buildings) {

	public PlayerInfoProfile {
		buildings = buildings == null ? List.of() : List.copyOf(buildings);
	}

	public boolean hasDetails() {
		return registrationDate != null || lastLogin != null || about != null || city != null
				|| telegram != null || vk != null || website != null || clan != null || marriage != null
				|| !buildings.isEmpty();
	}

	public record Clan(String tag, String name, String leaderName, boolean playerIsLeader) { }
	public record Marriage(String partner, String date, String surname) { }
	public record Building(String title, String rating) { }
}
