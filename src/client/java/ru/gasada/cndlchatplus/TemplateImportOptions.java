package ru.gasada.cndlchatplus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TemplateImportOptions {
	public enum Category {
		CHANNELS_AND_MARKERS,
		MUTED_WORDS,
		MUTED_MINECRAFT_PLAYERS,
		MUTED_DISCORD_USERS,
		DISCORD_SETTINGS,
		FRIENDS,
		LAST_SEEN,
		HUD_AND_SOUND,
		COMMANDS,
		PARSER_PATTERNS,
		PLAYER_INFO
	}

	public enum ListMode {
		REPLACE,
		MERGE,
		SKIP
	}

	private final Set<Category> selected = EnumSet.noneOf(Category.class);
	private final Map<Category, ListMode> listModes = new EnumMap<>(Category.class);
	private boolean overwriteExistingLastSeen;

	public TemplateImportOptions() {
		for (Category category : Category.values()) {
			listModes.put(category, ListMode.MERGE);
		}
	}

	public static TemplateImportOptions all() {
		TemplateImportOptions options = new TemplateImportOptions();
		options.selected.addAll(EnumSet.allOf(Category.class));
		return options;
	}

	public TemplateImportOptions select(Category category, boolean value) {
		if (value) {
			selected.add(category);
		} else {
			selected.remove(category);
		}
		return this;
	}

	public boolean selected(Category category) {
		return selected.contains(category);
	}

	public TemplateImportOptions listMode(Category category, ListMode mode) {
		listModes.put(category, mode);
		return this;
	}

	public ListMode listMode(Category category) {
		return listModes.getOrDefault(category, ListMode.MERGE);
	}

	public TemplateImportOptions overwriteExistingLastSeen(boolean value) {
		overwriteExistingLastSeen = value;
		return this;
	}

	public boolean overwriteExistingLastSeen() {
		return overwriteExistingLastSeen;
	}
}
