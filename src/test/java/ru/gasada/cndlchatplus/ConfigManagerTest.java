package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigManagerTest {
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	@TempDir
	Path directory;

	@Test
	void globalSaveMergesOnlyGlobalFieldsAndPreservesRawUnknownBridgeAndVanillaBox() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		String persisted = """
				{
				  "storageVersion": null,
				  "unknownFuture": {"ordered":[3,null,1]},
				  "enabled": false,
				  "rules": [null,{"trigger":null,"response":"reply","channel":null}],
				  "periodicMessages": [null,{"enabled":true,"message":null,"intervalMinutes":-4}],
				  "friends": ["PersistedFriend"],
				  "mutedWords": ["persisted-word"],
				  "chatTabsEnabled": true,
				  "friendHudEnabled": true
				}
				""";
		Files.writeString(main, persisted, StandardCharsets.UTF_8);
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig vanilla = persistedVanilla();
		assertTrue(store.save(vanilla).success());
		byte[] vanillaBefore = Files.readAllBytes(directory.resolve("server-templates/vanilla-box.json"));
		JsonObject before = JsonParser.parseString(persisted).getAsJsonObject();
		ResponderConfig edited = ResponderConfig.defaults();
		edited.chatTabsEnabled = false;
		edited.customChatTabs = new ArrayList<>();
		edited.hiddenBuiltInTabs = new ArrayList<>(List.of("GLOBAL"));
		edited.friendHudEnabled = false;
		edited.friends = new ArrayList<>(List.of("WrongFriend"));
		edited.mutedWords = new ArrayList<>(List.of("wrong-word"));
		edited.rules = null;
		edited.periodicMessages = null;

		assertTrue(ConfigManager.saveGlobalSettings(edited, main));

		JsonObject after = JsonParser.parseString(Files.readString(main)).getAsJsonObject();
		assertFalse(after.get("chatTabsEnabled").getAsBoolean());
		assertFalse(after.get("friendHudEnabled").getAsBoolean());
		assertEquals(0, after.getAsJsonArray("customChatTabs").size());
		assertEquals("GLOBAL", after.getAsJsonArray("hiddenBuiltInTabs").get(0).getAsString());
		assertEquals(before.get("storageVersion"), after.get("storageVersion"));
		assertEquals(before.get("unknownFuture"), after.get("unknownFuture"));
		assertEquals(before.get("enabled"), after.get("enabled"));
		assertEquals(before.get("rules"), after.get("rules"));
		assertEquals(before.get("periodicMessages"), after.get("periodicMessages"));
		assertEquals(before.get("friends"), after.get("friends"));
		assertEquals(before.get("mutedWords"), after.get("mutedWords"));
		assertArrayEquals(vanillaBefore,
				Files.readAllBytes(directory.resolve("server-templates/vanilla-box.json")));
	}

	@Test
	void loadProjectsExactNullableAutomationBridgeFromFixedVanillaBox() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		Files.writeString(main, "{\"storageVersion\":1}", StandardCharsets.UTF_8);
		VanillaBoxConfig vanilla = persistedVanilla();
		vanilla.responderEnabled = false;
		vanilla.rules = null;
		vanilla.periodicMessages = new ArrayList<>(Arrays.asList(
				new PeriodicMessageConfig(true, null, -3), null,
				new PeriodicMessageConfig(false, "last", 7)));
		vanilla.clanReplyPrefix = null;
		vanilla.privateReplyCommand = null;
		assertTrue(new VanillaBoxConfigStore(directory).save(vanilla).success());

		ConfigManager.LoadedConfig loaded = ConfigManager.load(main);

		assertTrue(loaded.vanillaBox().success(), loaded.vanillaBox().errorMessage());
		assertFalse(loaded.config().enabled);
		assertNull(loaded.config().rules);
		assertEquals(3, loaded.config().periodicMessages.size());
		assertNull(loaded.config().periodicMessages.getFirst().message);
		assertNull(loaded.config().periodicMessages.get(1));
		assertEquals("last", loaded.config().periodicMessages.get(2).message);
		assertNull(loaded.config().clanReplyPrefix);
		assertNull(loaded.config().privateReplyCommand);
	}

	@Test
	void visibleSaveWritesCompatibleViewThenFixedVanillaAndPublishesExactReload() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig vanilla = persistedVanilla();
		assertTrue(store.save(vanilla).success());
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(vanilla);
		ResponderConfig edited = visibleConfig();

		assertTrue(ConfigManager.save(edited, main, runtime));

		ResponderConfig compatible = ResponderConfigJson.read(GSON, Files.readString(main));
		VanillaBoxConfig reloaded = store.load().value();
		assertVisible(compatible);
		assertVisible(reloaded);
		assertEquals(3, reloaded.rules.size());
		assertNull(reloaded.rules.get(1));
		assertEquals("bridge-last", reloaded.rules.get(2).trigger);
		assertEquals(3, reloaded.periodicMessages.size());
		assertNull(reloaded.periodicMessages.get(1));
		assertEquals("custom {player} {message}", reloaded.commands.privateMessage);
		assertVisible(runtime.activeSnapshot().orElseThrow());
	}

	@Test
	void inactiveVisibleSaveWritesBothFilesWithoutReactivatingRuntime() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig vanilla = persistedVanilla();
		assertTrue(store.save(vanilla).success());
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(vanilla);
		runtime.clear();
		long generation = runtime.generation();

		assertTrue(ConfigManager.save(visibleConfig(), main, runtime));

		assertVisible(ResponderConfigJson.read(GSON, Files.readString(main)));
		assertVisible(store.load().value());
		assertTrue(runtime.activeSnapshot().isEmpty());
		assertEquals(generation, runtime.generation());
	}

	@Test
	void failedFixedWriteReturnsFalseAndRetainsExactRuntimeSnapshotAndGeneration() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig prior = persistedVanilla();
		prior.friends = new ArrayList<>(List.of("Prior"));
		assertTrue(store.save(prior).success());
		byte[] fixedBefore = Files.readAllBytes(directory.resolve("server-templates/vanilla-box.json"));
		Path blockedTemporary = directory.resolve("server-templates/vanilla-box.json.tmp");
		Files.createDirectories(blockedTemporary);
		Files.writeString(blockedTemporary.resolve("block"), "block", StandardCharsets.UTF_8);
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		VanillaBoxSnapshot snapshot = runtime.activate(prior);
		long generation = runtime.generation();
		ResponderConfig memory = visibleConfig();
		FriendActionService actions = new FriendActionService(runtime, null, memory, main);
		assertFalse(actions.addFriend("Carol"));
		assertEquals(List.of("Alice", "Bob"), memory.friends);

		assertFalse(ConfigManager.save(visibleConfig(), main, runtime));

		assertSame(snapshot, runtime.activeSnapshot().orElseThrow());
		assertEquals(generation, runtime.generation());
		assertArrayEquals(fixedBefore, Files.readAllBytes(directory.resolve("server-templates/vanilla-box.json")));
		assertVisible(ResponderConfigJson.read(GSON, Files.readString(main)));
	}

	@Test
	void friendAddAndLastSeenUseFixedPersistenceAndLastSeenPreservesLookupQueue() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig vanilla = persistedVanilla();
		vanilla.friends = new ArrayList<>(List.of("Alice", "Bob"));
		assertTrue(store.save(vanilla).success());
		ResponderConfig compatible = visibleConfig();
		compatible.friends = new ArrayList<>(vanilla.friends);
		RuntimeResetCoordinator coordinator = new RuntimeResetCoordinator();
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(coordinator);
		runtime.activate(vanilla);
		FriendActionService actions = new FriendActionService(runtime, null, compatible, main);
		FriendLookupManager lookup = new FriendLookupManager(runtime, actions, System::currentTimeMillis);
		coordinator.register(lookup::resetRuntimeState);
		lookup.queueFriends(vanilla.friends);

		assertTrue(actions.addFriend("Carol"));
		lookup.queueFriends(List.of("Alice", "Bob"));
		int queuedBefore = lookup.queuedCount();
		assertTrue(actions.updateLastSeen("alice", "вчера"));

		assertEquals(queuedBefore, lookup.queuedCount());
		VanillaBoxConfig reloaded = store.load().value();
		assertEquals(List.of("Alice", "Bob", "Carol"), reloaded.friends);
		assertEquals("вчера", reloaded.friendLastSeen.get("Alice"));
		assertEquals("вчера", runtime.activeSnapshot().orElseThrow().friendLastSeen().get("Alice"));
	}

	@Test
	void lastSeenUpdatesOnlyThatFieldAndFailedPersistenceRetainsMemoryAndRuntime() throws Exception {
		Path main = directory.resolve("cndl-chat-plus.json");
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig vanilla = persistedVanilla();
		vanilla.friends = new ArrayList<>(List.of("Alice"));
		vanilla.mutedWords = new ArrayList<>(List.of("fixed-word"));
		assertTrue(store.save(vanilla).success());
		ResponderConfig compatible = visibleConfig();
		compatible.friends = new ArrayList<>(List.of("Alice"));
		compatible.mutedWords = new ArrayList<>(List.of("stale-word"));
		VanillaBoxRuntime runtime = new VanillaBoxRuntime(new RuntimeResetCoordinator());
		runtime.activate(vanilla);
		FriendActionService actions = new FriendActionService(runtime, null, compatible, main);

		assertTrue(actions.updateLastSeen("alice", "вчера"));
		assertEquals(List.of("fixed-word"), store.load().value().mutedWords);
		VanillaBoxSnapshot priorSnapshot = runtime.activeSnapshot().orElseThrow();
		long priorGeneration = runtime.generation();
		Path blockedTemporary = directory.resolve("server-templates/vanilla-box.json.tmp");
		Files.createDirectories(blockedTemporary);
		Files.writeString(blockedTemporary.resolve("block"), "block", StandardCharsets.UTF_8);

		assertFalse(actions.updateLastSeen("Alice", "сегодня"));
		assertEquals("вчера", compatible.friendLastSeen.get("Alice"));
		assertSame(priorSnapshot, runtime.activeSnapshot().orElseThrow());
		assertEquals(priorGeneration, runtime.generation());
		assertEquals("вчера", store.load().value().friendLastSeen.get("Alice"));
	}

	private static VanillaBoxConfig persistedVanilla() {
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.rules = new ArrayList<>(Arrays.asList(
				new ReplyRule(null, "first", null), null,
				new ReplyRule("bridge-last", null, ChatChannel.PRIVATE)));
		config.periodicMessages = new ArrayList<>(Arrays.asList(
				new PeriodicMessageConfig(true, null, -2), null,
				new PeriodicMessageConfig(false, "last", 9)));
		config.commands.privateMessage = "custom {player} {message}";
		config.parsers = ParserSettings.vanillaBoxDefaults();
		return config;
	}

	private static ResponderConfig visibleConfig() {
		ResponderConfig config = ResponderConfig.defaults();
		config.discordMutedPlayers = new ArrayList<>(List.of("DiscordMuted"));
		config.mutedWords = new ArrayList<>(List.of("blacklisted"));
		config.friends = new ArrayList<>(List.of("Alice", "Bob"));
		config.friendLastSeen.put("Alice", "сегодня");
		config.teleportAutoAcceptMode = TeleportAutoAcceptMode.SELECTED_FRIENDS;
		config.teleportAutoAcceptFriends = new ArrayList<>(List.of("Bob"));
		return config;
	}

	private static void assertVisible(ResponderConfig config) {
		assertEquals(List.of("DiscordMuted"), config.discordMutedPlayers);
		assertEquals(List.of("blacklisted"), config.mutedWords);
		assertEquals(List.of("Alice", "Bob"), config.friends);
		assertEquals("сегодня", config.friendLastSeen.get("Alice"));
		assertEquals(TeleportAutoAcceptMode.SELECTED_FRIENDS, config.teleportAutoAcceptMode);
		assertEquals(List.of("Bob"), config.teleportAutoAcceptFriends);
	}

	private static void assertVisible(VanillaBoxConfig config) {
		assertEquals(List.of("DiscordMuted"), config.discordMutedPlayers);
		assertEquals(List.of("blacklisted"), config.mutedWords);
		assertEquals(List.of("Alice", "Bob"), config.friends);
		assertEquals("сегодня", config.friendLastSeen.get("Alice"));
		assertEquals(TeleportAutoAcceptMode.SELECTED_FRIENDS, config.teleportAutoAcceptMode);
		assertEquals(List.of("Bob"), config.teleportAutoAcceptFriends);
	}

	private static void assertVisible(VanillaBoxSnapshot snapshot) {
		assertEquals(List.of("DiscordMuted"), snapshot.discordMutedPlayers());
		assertEquals(List.of("blacklisted"), snapshot.mutedWords());
		assertEquals(List.of("Alice", "Bob"), snapshot.friends());
		assertEquals("сегодня", snapshot.friendLastSeen().get("Alice"));
		assertEquals(TeleportAutoAcceptMode.SELECTED_FRIENDS, snapshot.teleportAutoAcceptMode());
		assertEquals(List.of("Bob"), snapshot.teleportAutoAcceptFriends());
	}
}
