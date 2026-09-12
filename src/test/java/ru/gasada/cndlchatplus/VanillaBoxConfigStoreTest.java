package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VanillaBoxConfigStoreTest {
	private static final Path RELATIVE_CONFIG_PATH = Path.of("server-templates", "vanilla-box.json");

	@TempDir
	Path directory;

	@Test
	void roundTripsPersistedCustomCommandsAndParsersWithoutApplyingBootstrapDefaults() {
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.commands.privateMessage = "custom-message {player} {message}";
		config.commands.acceptTeleport = "custom-accept";
		config.commands.claimFly = "";
		config.parsers.lastSeenPattern = "custom-last-seen-(.+)";
		config.parsers.playerInfoPatterns.put("Custom", "custom-(.+)");

		assertTrue(store.save(config).success());
		VanillaBoxConfig loaded = store.load().value();

		assertEquals("custom-message {player} {message}", loaded.commands.privateMessage);
		assertEquals("custom-accept", loaded.commands.acceptTeleport);
		assertEquals("", loaded.commands.claimFly);
		assertEquals("custom-last-seen-(.+)", loaded.parsers.lastSeenPattern);
		assertEquals(List.of("Custom"), new ArrayList<>(loaded.parsers.playerInfoPatterns.keySet()));
	}

	@Test
	void preservesExplicitNullAutomationValuesAndListOrder() {
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		ReplyRule firstRule = new ReplyRule(null, null, null);
		firstRule.enabled = false;
		config.responderEnabled = false;
		config.rules = new ArrayList<>(Arrays.asList(firstRule, null,
				new ReplyRule("last", "response", ChatChannel.PRIVATE)));
		config.clanReplyPrefix = null;
		config.privateReplyCommand = null;
		config.periodicMessages = new ArrayList<>(Arrays.asList(
				new PeriodicMessageConfig(true, null, -3),
				null,
				new PeriodicMessageConfig(false, "last", 17)));

		assertTrue(store.save(config).success());
		VanillaBoxConfig loaded = store.load().value();

		assertFalse(loaded.responderEnabled);
		assertEquals(3, loaded.rules.size());
		assertFalse(loaded.rules.getFirst().enabled);
		assertNull(loaded.rules.getFirst().trigger);
		assertNull(loaded.rules.getFirst().response);
		assertNull(loaded.rules.getFirst().channel);
		assertNull(loaded.rules.get(1));
		assertEquals("last", loaded.rules.get(2).trigger);
		assertNull(loaded.clanReplyPrefix);
		assertNull(loaded.privateReplyCommand);
		assertEquals(3, loaded.periodicMessages.size());
		assertNull(loaded.periodicMessages.getFirst().message);
		assertEquals(-3, loaded.periodicMessages.getFirst().intervalMinutes);
		assertNull(loaded.periodicMessages.get(1));
		assertEquals("last", loaded.periodicMessages.get(2).message);
		assertEquals(17, loaded.periodicMessages.get(2).intervalMinutes);
	}

	@Test
	void preservesNullableAutomationCollections() {
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig config = VanillaBoxConfig.empty();
		config.rules = null;
		config.periodicMessages = null;

		assertTrue(store.save(config).success());
		VanillaBoxConfig loaded = store.load().value();

		assertNull(loaded.rules);
		assertNull(loaded.periodicMessages);
	}

	@Test
	void loadsArbitrarySerializedIdentityAsFixedWithoutChangingData() throws Exception {
		Path path = directory.resolve(RELATIVE_CONFIG_PATH);
		Files.createDirectories(path.getParent());
		Files.writeString(path, """
				{"id":"other","name":"Other","globalPrefix":"$","responderEnabled":false,
				"rules":[null,{"enabled":false,"trigger":null,"response":"bridge","channel":null}],
				"clanReplyPrefix":null,"periodicMessages":[null,{"enabled":true,"message":null,"intervalMinutes":7}]}
				""", StandardCharsets.UTF_8);

		VanillaBoxConfig loaded = new VanillaBoxConfigStore(directory).load().value();

		assertEquals(VanillaBoxConfig.ID, loaded.id);
		assertEquals(VanillaBoxConfig.NAME, loaded.name);
		assertEquals("$", loaded.globalPrefix);
		assertFalse(loaded.responderEnabled);
		assertEquals(2, loaded.rules.size());
		assertNull(loaded.rules.getFirst());
		assertFalse(loaded.rules.get(1).enabled);
		assertNull(loaded.rules.get(1).trigger);
		assertEquals("bridge", loaded.rules.get(1).response);
		assertNull(loaded.rules.get(1).channel);
		assertNull(loaded.clanReplyPrefix);
		assertEquals(2, loaded.periodicMessages.size());
		assertNull(loaded.periodicMessages.getFirst());
		assertTrue(loaded.periodicMessages.get(1).enabled);
		assertNull(loaded.periodicMessages.get(1).message);
		assertEquals(7, loaded.periodicMessages.get(1).intervalMinutes);
	}

	@Test
	void returnsFailureWhenFixedConfigIsMissing() {
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);

		ConfigOperationResult<VanillaBoxConfig> result = store.load();

		assertFalse(result.success());
		assertNull(result.value());
	}

	@Test
	void returnsFailureWhenFixedConfigIsCorrupt() throws Exception {
		Path path = directory.resolve(RELATIVE_CONFIG_PATH);
		Files.createDirectories(path.getParent());
		Files.writeString(path, "{broken", StandardCharsets.UTF_8);
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);

		ConfigOperationResult<VanillaBoxConfig> result = store.load();

		assertFalse(result.success());
		assertNull(result.value());
		assertTrue(result.error() != null);
	}

	@Test
	void failedAtomicWriteLeavesPriorConfigBytesReadableAndUnchanged() throws Exception {
		VanillaBoxConfigStore store = new VanillaBoxConfigStore(directory);
		VanillaBoxConfig prior = VanillaBoxConfig.empty();
		prior.globalPrefix = "prior";
		assertTrue(store.save(prior).success());
		Path path = directory.resolve(RELATIVE_CONFIG_PATH);
		byte[] priorBytes = Files.readAllBytes(path);
		Path blockedTemporary = path.resolveSibling("vanilla-box.json.tmp");
		Files.createDirectories(blockedTemporary);
		Files.writeString(blockedTemporary.resolve("block"), "block", StandardCharsets.UTF_8);
		VanillaBoxConfig replacement = VanillaBoxConfig.empty();
		replacement.globalPrefix = "replacement";

		ConfigOperationResult<Void> result = store.save(replacement);

		assertFalse(result.success());
		assertArrayEquals(priorBytes, Files.readAllBytes(path));
		assertEquals("prior", store.load().value().globalPrefix);
	}

	@Test
	void rejectsSymlinkedConfigWithoutReadingExternalTarget() throws Exception {
		Path externalRoot = directory.resolve("external");
		VanillaBoxConfig external = VanillaBoxConfig.empty();
		external.globalPrefix = "external";
		assertTrue(new VanillaBoxConfigStore(externalRoot).save(external).success());
		Path externalConfig = externalRoot.resolve(RELATIVE_CONFIG_PATH);
		byte[] externalBytes = Files.readAllBytes(externalConfig);
		Path path = directory.resolve(RELATIVE_CONFIG_PATH);
		Files.createDirectories(path.getParent());
		Files.createSymbolicLink(path, externalConfig);

		ConfigOperationResult<VanillaBoxConfig> result = new VanillaBoxConfigStore(directory).load();

		assertFalse(result.success());
		assertNull(result.value());
		assertArrayEquals(externalBytes, Files.readAllBytes(externalConfig));
		assertTrue(Files.isSymbolicLink(path));
	}

	@Test
	void rejectsSymlinkedConfigDirectoryWithoutReadingExternalTarget() throws Exception {
		Path externalRoot = directory.resolve("external");
		VanillaBoxConfig external = VanillaBoxConfig.empty();
		external.globalPrefix = "external";
		assertTrue(new VanillaBoxConfigStore(externalRoot).save(external).success());
		Path externalConfig = externalRoot.resolve(RELATIVE_CONFIG_PATH);
		byte[] externalBytes = Files.readAllBytes(externalConfig);
		Path templates = directory.resolve("server-templates");
		Files.createSymbolicLink(templates, externalRoot.resolve("server-templates"));

		ConfigOperationResult<VanillaBoxConfig> result = new VanillaBoxConfigStore(directory).load();

		assertFalse(result.success());
		assertNull(result.value());
		assertArrayEquals(externalBytes, Files.readAllBytes(externalConfig));
		assertTrue(Files.isSymbolicLink(templates));
	}

	@Test
	void rejectsSymlinkedConfigRootWithoutReadingExternalTarget() throws Exception {
		Path externalRoot = directory.resolve("external");
		VanillaBoxConfig external = VanillaBoxConfig.empty();
		external.globalPrefix = "external";
		assertTrue(new VanillaBoxConfigStore(externalRoot).save(external).success());
		Path externalConfig = externalRoot.resolve(RELATIVE_CONFIG_PATH);
		byte[] externalBytes = Files.readAllBytes(externalConfig);
		Path linkedRoot = directory.resolve("linked-root");
		Files.createSymbolicLink(linkedRoot, externalRoot);

		ConfigOperationResult<VanillaBoxConfig> result = new VanillaBoxConfigStore(linkedRoot).load();

		assertFalse(result.success());
		assertNull(result.value());
		assertArrayEquals(externalBytes, Files.readAllBytes(externalConfig));
		assertTrue(Files.isSymbolicLink(linkedRoot));
	}

	@Test
	void saveRejectsSymlinkedTargetWithoutOverwritingExternalTarget() throws Exception {
		Path externalTarget = directory.resolve("external.json");
		byte[] externalBytes = "external".getBytes(StandardCharsets.UTF_8);
		Files.write(externalTarget, externalBytes);
		Path path = directory.resolve(RELATIVE_CONFIG_PATH);
		Files.createDirectories(path.getParent());
		Files.createSymbolicLink(path, externalTarget);

		ConfigOperationResult<Void> result = new VanillaBoxConfigStore(directory).save(VanillaBoxConfig.empty());

		assertFalse(result.success());
		assertArrayEquals(externalBytes, Files.readAllBytes(externalTarget));
		assertTrue(Files.isSymbolicLink(path));
	}

	@Test
	void saveRejectsSymlinkedTemporaryWithoutOverwritingExternalTarget() throws Exception {
		Path externalTarget = directory.resolve("external.tmp");
		byte[] externalBytes = "external".getBytes(StandardCharsets.UTF_8);
		Files.write(externalTarget, externalBytes);
		Path path = directory.resolve(RELATIVE_CONFIG_PATH);
		Files.createDirectories(path.getParent());
		Path temporary = path.resolveSibling("vanilla-box.json.tmp");
		Files.createSymbolicLink(temporary, externalTarget);

		ConfigOperationResult<Void> result = new VanillaBoxConfigStore(directory).save(VanillaBoxConfig.empty());

		assertFalse(result.success());
		assertArrayEquals(externalBytes, Files.readAllBytes(externalTarget));
		assertFalse(Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS));
	}

	@Test
	void saveRejectsSymlinkedConfigDirectoryWithoutWritingExternalTarget() throws Exception {
		Path externalTemplates = Files.createDirectory(directory.resolve("external-templates"));
		Path templates = directory.resolve("server-templates");
		Files.createSymbolicLink(templates, externalTemplates);
		Path externalConfig = externalTemplates.resolve("vanilla-box.json");

		ConfigOperationResult<Void> result = new VanillaBoxConfigStore(directory).save(VanillaBoxConfig.empty());

		assertFalse(result.success());
		assertFalse(Files.exists(externalConfig));
		assertTrue(Files.isSymbolicLink(templates));
	}

	@Test
	void exposesOnlyFixedLoadAndSaveOperationsWithoutSelectorParameters() {
		List<Method> operations = Arrays.stream(VanillaBoxConfigStore.class.getDeclaredMethods())
				.filter(method -> !Modifier.isPrivate(method.getModifiers()) && !method.isSynthetic())
				.sorted(java.util.Comparator.comparing(Method::getName))
				.toList();

		assertFalse(Modifier.isPublic(VanillaBoxConfigStore.class.getModifiers()));
		assertEquals(List.of("load", "save"), operations.stream().map(Method::getName).toList());
		assertEquals(List.of(0, 1), operations.stream().map(Method::getParameterCount).toList());
		assertTrue(operations.stream().flatMap(method -> Arrays.stream(method.getParameterTypes()))
				.noneMatch(type -> type == String.class || type == Path.class));
	}
}
