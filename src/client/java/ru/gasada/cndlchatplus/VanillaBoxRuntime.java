package ru.gasada.cndlchatplus;

import java.util.Optional;
import java.util.function.Consumer;

final class VanillaBoxRuntime {
	private final RuntimeResetCoordinator resetCoordinator;
	private volatile VanillaBoxSnapshot snapshot;
	private VanillaBoxConfig config;
	private volatile long generation;

	VanillaBoxRuntime(RuntimeResetCoordinator resetCoordinator) {
		this.resetCoordinator = resetCoordinator;
	}

	synchronized VanillaBoxSnapshot activate(VanillaBoxConfig config) {
		if (config == null) {
			throw new IllegalArgumentException("config must not be null");
		}
		VanillaBoxConfig nextConfig = config.deepCopy();
		nextConfig.sanitize();
		VanillaBoxSnapshot nextSnapshot = VanillaBoxSnapshot.from(nextConfig, generation + 1);
		resetCoordinator.resetAll();
		this.config = nextConfig;
		generation = nextSnapshot.generation();
		snapshot = nextSnapshot;
		return snapshot;
	}

	synchronized Optional<VanillaBoxSnapshot> update(Consumer<VanillaBoxConfig> update) {
		if (config == null) {
			return Optional.empty();
		}
		VanillaBoxConfig nextConfig = config.deepCopy();
		update.accept(nextConfig);
		nextConfig.sanitize();
		VanillaBoxSnapshot nextSnapshot = VanillaBoxSnapshot.from(nextConfig, generation + 1);
		resetCoordinator.resetAll();
		config = nextConfig;
		generation = nextSnapshot.generation();
		snapshot = nextSnapshot;
		return Optional.of(snapshot);
	}

	synchronized Optional<VanillaBoxSnapshot> updateLastSeen(String player, String value) {
		if (config == null) {
			return Optional.empty();
		}
		VanillaBoxConfig nextConfig = config.deepCopy();
		nextConfig.friendLastSeen.keySet().removeIf(key -> key.equalsIgnoreCase(player));
		nextConfig.friendLastSeen.put(player, value);
		VanillaBoxSnapshot nextSnapshot = VanillaBoxSnapshot.from(nextConfig, generation + 1);
		config = nextConfig;
		generation = nextSnapshot.generation();
		snapshot = nextSnapshot;
		return Optional.of(nextSnapshot);
	}

	synchronized void clear() {
		resetCoordinator.resetAll();
		snapshot = null;
		config = null;
		generation++;
	}

	Optional<VanillaBoxSnapshot> activeSnapshot() {
		return Optional.ofNullable(snapshot);
	}

	long generation() {
		return generation;
	}
}
