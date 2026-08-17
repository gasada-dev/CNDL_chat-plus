package ru.gasada.cndlchatplus;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;

public final class PlayerSuggestionProvider {
	public List<String> suggest(Minecraft minecraft, String query, int limit) {
		if (minecraft == null || minecraft.getConnection() == null || limit < 1) return List.of();
		String normalizedQuery = query.toLowerCase(Locale.ROOT);
		String ownName = minecraft.getUser().getName();
		return minecraft.getConnection().getListedOnlinePlayers().stream()
				.map(info -> info.getProfile().name())
				.filter(name -> !name.equalsIgnoreCase(ownName))
				.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(normalizedQuery))
				.sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
				.distinct().limit(limit).toList();
	}
}
