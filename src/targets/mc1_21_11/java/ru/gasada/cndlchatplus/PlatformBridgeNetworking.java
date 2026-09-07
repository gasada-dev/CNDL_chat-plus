package ru.gasada.cndlchatplus;

import java.util.concurrent.CompletableFuture;

final class PlatformBridgeNetworking {
	private PlatformBridgeNetworking() { }
	static void register() { }
	static void connected() { }
	static void disconnected() { }
	static boolean available() { return false; }
	static CompletableFuture<VnbxPlayerRelationsResult> requestPlayerRelations(String requestId, String player) {
		VnbxBridgeClient.logRequestFailed(requestId, player, "bridge_unavailable");
		return CompletableFuture.completedFuture(VnbxPlayerRelationsResult.unavailable(requestId, player));
	}
}
