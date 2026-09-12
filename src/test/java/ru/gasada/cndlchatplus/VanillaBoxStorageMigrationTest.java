package ru.gasada.cndlchatplus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VanillaBoxStorageMigrationTest {
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();
	private static final Path VANILLA_PATH = Path.of("server-templates", "vanilla-box.json");

	@TempDir
	Path directory;

	@Test
	void freshInstallInitializesVanillaAndWritesMarkerLast() {
		ConfigOperationResult<VanillaBoxConfig> result = migrate();

		assertTrue(result.success(), result.errorMessage());
		assertEquals("clan lookup {player}", result.value().commands.lookupFriend);
		assertFalse(result.value().parsers.lookupOutputPattern.isBlank());
		assertEquals(1, readMain().storageVersion);
		assertTrue(new VanillaBoxConfigStore(directory).load().success());
	}

	@Test
	void existingValidVanillaBoxWinsOverCompatibleConfig() throws IOException {
		VanillaBoxConfig existing = VanillaBoxConfig.empty();
		existing.globalPrefix = "persisted";
		existing.commands.privateMessage = "persisted {player} {message}";
		assertTrue(new VanillaBoxConfigStore(directory).save(existing).success());
		writeMain("""
				{"storageVersion":0,"globalPrefix":"compatible"}
				""");

		ConfigOperationResult<VanillaBoxConfig> result = migrate();

		assertTrue(result.success(), result.errorMessage());
		assertEquals("persisted", result.value().globalPrefix);
		assertEquals("persisted {player} {message}", result.value().commands.privateMessage);
		assertEquals(1, readMain().storageVersion);
	}

	@Test
	void compatibleMigrationPreservesExactAutomationBridgeAndArchivesOnlyRetiredPaths() throws IOException {
		ResponderConfig compatible = ResponderConfig.defaults();
		compatible.storageVersion = 0;
		compatible.enabled = false;
		ReplyRule first = new ReplyRule(null, "first", null);
		first.enabled = false;
		compatible.rules = new ArrayList<>(Arrays.asList(first, null,
				new ReplyRule("last", null, ChatChannel.PRIVATE)));
		compatible.clanReplyPrefix = null;
		compatible.privateReplyCommand = null;
		compatible.periodicMessages = new ArrayList<>(Arrays.asList(
				new PeriodicMessageConfig(true, null, -7), null,
				new PeriodicMessageConfig(false, "last", 19)));
		writeMain(GSON.toJson(compatible));
		JsonObject originalMain = JsonParser.parseString(Files.readString(mainPath())).getAsJsonObject();
		byte[] root = write("server-templates.json", "root");
		byte[] other = write("server-templates/other.json", "other");
		byte[] imported = write("cndl-chat-plus-template-imports/nested/raw.bin", "import");
		byte[] backup = write("cndl-chat-plus.legacy-backup.json", "backup");
		byte[] branded = write("gasada-chat-responder-template-imports/old.json", "branded");

		ConfigOperationResult<VanillaBoxConfig> result = migrate();

		assertTrue(result.success(), result.errorMessage());
		VanillaBoxConfig migrated = result.value();
		assertFalse(migrated.responderEnabled);
		assertEquals(GSON.toJson(compatible.rules), GSON.toJson(migrated.rules));
		assertEquals(GSON.toJson(compatible.periodicMessages), GSON.toJson(migrated.periodicMessages));
		assertNull(migrated.clanReplyPrefix);
		assertNull(migrated.privateReplyCommand);
		JsonObject completedMain = JsonParser.parseString(Files.readString(mainPath())).getAsJsonObject();
		assertEquals(originalMain.get("rules"), completedMain.get("rules"));
		assertEquals(originalMain.get("periodicMessages"), completedMain.get("periodicMessages"));
		assertEquals(originalMain.get("clanReplyPrefix"), completedMain.get("clanReplyPrefix"));
		assertEquals(originalMain.get("privateReplyCommand"), completedMain.get("privateReplyCommand"));
		assertArchived("server-templates.json", root);
		assertArchived("server-templates/other.json", other);
		assertArchived("cndl-chat-plus-template-imports/nested/raw.bin", imported);
		assertFalse(Files.exists(directory.resolve("server-templates.json")));
		assertFalse(Files.exists(directory.resolve("server-templates/other.json")));
		assertFalse(Files.exists(directory.resolve("cndl-chat-plus-template-imports")));
		assertArrayEquals(backup, Files.readAllBytes(directory.resolve("cndl-chat-plus.legacy-backup.json")));
		assertArrayEquals(branded,
				Files.readAllBytes(directory.resolve("gasada-chat-responder-template-imports/old.json")));
	}

	@Test
	void compatibleMigrationDerivesPeriodicEntryFromLegacySingletonWithoutMutatingMain() throws IOException {
		writeMain("""
				{"storageVersion":0,"periodicMessages":null,"periodicEnabled":false,
				 "periodicMessage":null,"periodicIntervalMinutes":null}
				""");

		ConfigOperationResult<VanillaBoxConfig> result = migrate();

		assertTrue(result.success(), result.errorMessage());
		assertEquals(1, result.value().periodicMessages.size());
		assertFalse(result.value().periodicMessages.getFirst().enabled);
		assertEquals("", result.value().periodicMessages.getFirst().message);
		assertEquals(5, result.value().periodicMessages.getFirst().intervalMinutes);
		ResponderConfig main = readMain();
		assertNull(main.periodicMessages);
		assertFalse(main.periodicEnabled);
		assertNull(main.periodicMessage);
		assertNull(main.periodicIntervalMinutes);
	}

	@Test
	void corruptMainIsTerminalAndPermitsNoCleanup() throws IOException {
		writeMain("{broken");
		byte[] retired = write("server-templates/other.json", "retired");

		ConfigOperationResult<VanillaBoxConfig> result = migrate();

		assertFalse(result.success());
		assertArrayEquals(retired, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertFalse(Files.exists(directory.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY)));
		assertFalse(Files.exists(directory.resolve(VANILLA_PATH)));
	}

	@Test
	void corruptPreV1VanillaBoxIsTerminalAndIsNotOverwritten() throws IOException {
		writeMain("{\"storageVersion\":0}");
		byte[] corrupt = write(VANILLA_PATH.toString(), "{broken");
		byte[] retired = write("server-templates/other.json", "retired");

		ConfigOperationResult<VanillaBoxConfig> result = migrate();

		assertFalse(result.success());
		assertArrayEquals(corrupt, Files.readAllBytes(directory.resolve(VANILLA_PATH)));
		assertArrayEquals(retired, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertEquals(0, readMain().storageVersion);
	}

	@Test
	void archiveFailureLeavesMarkerAndAllOriginalsUntouched() throws IOException {
		writeMain("{\"storageVersion\":0}");
		byte[] root = write("server-templates.json", "root");
		byte[] other = write("server-templates/other.json", "other");
		Files.writeString(directory.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY), "collision");

		ConfigOperationResult<VanillaBoxConfig> result = migrate();

		assertFalse(result.success());
		assertArrayEquals(root, Files.readAllBytes(directory.resolve("server-templates.json")));
		assertArrayEquals(other, Files.readAllBytes(directory.resolve("server-templates/other.json")));
		assertEquals(0, readMain().storageVersion);
	}

	@Test
	void interruptedDeletionResumesAgainstVerifiedFixedArchive() throws IOException {
		writeMain("{\"storageVersion\":0}");
		write("server-templates.json", "root");
		write("server-templates/other.json", "other");
		AtomicInteger deletes = new AtomicInteger();
		VanillaBoxStorageMigration interrupted = new VanillaBoxStorageMigration(mainPath(), path -> {
			if (deletes.incrementAndGet() == 2) throw new IOException("stop deletion");
			Files.delete(path);
		});

		assertFalse(interrupted.migrate().success());
		assertEquals(0, readMain().storageVersion);
		assertTrue(Files.exists(directory.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY)
				.resolve("manifest.json")));

		ConfigOperationResult<VanillaBoxConfig> resumed = migrate();

		assertTrue(resumed.success(), resumed.errorMessage());
		assertFalse(Files.exists(directory.resolve("server-templates.json")));
		assertFalse(Files.exists(directory.resolve("server-templates/other.json")));
		assertEquals(1, readMain().storageVersion);
	}

	@Test
	void finalMarkerFailureRetriesWithoutRecopyingArchive() throws IOException {
		writeMain("{\"storageVersion\":0}");
		write("server-templates/other.json", "other");
		Path blockedTemporary = mainPath().resolveSibling("cndl-chat-plus.json.tmp");
		Files.createDirectories(blockedTemporary);
		Files.writeString(blockedTemporary.resolve("block"), "block");

		ConfigOperationResult<VanillaBoxConfig> failed = migrate();

		assertFalse(failed.success());
		assertEquals(0, readMain().storageVersion);
		assertFalse(Files.exists(directory.resolve("server-templates/other.json")));
		Path manifest = directory.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY).resolve("manifest.json");
		byte[] manifestBytes = Files.readAllBytes(manifest);
		Files.delete(blockedTemporary.resolve("block"));
		Files.delete(blockedTemporary);

		assertTrue(migrate().success());
		assertArrayEquals(manifestBytes, Files.readAllBytes(manifest));
		assertEquals(1, readMain().storageVersion);
		assertTrue(migrate().success());
		assertArrayEquals(manifestBytes, Files.readAllBytes(manifest));
	}

	@Test
	void versionOneNeverSynthesizesMissingOrCorruptVanillaBox() throws IOException {
		writeMain("{\"storageVersion\":1}");

		ConfigOperationResult<VanillaBoxConfig> missing = migrate();

		assertFalse(missing.success());
		assertFalse(Files.exists(directory.resolve(VANILLA_PATH)));
		write(VANILLA_PATH.toString(), "{broken");

		ConfigOperationResult<VanillaBoxConfig> corrupt = migrate();

		assertFalse(corrupt.success());
		assertEquals("{broken", Files.readString(directory.resolve(VANILLA_PATH)));
		assertEquals(1, readMain().storageVersion);
	}

	private ConfigOperationResult<VanillaBoxConfig> migrate() {
		return new VanillaBoxStorageMigration(mainPath()).migrate();
	}

	private Path mainPath() {
		return directory.resolve("cndl-chat-plus.json");
	}

	private void writeMain(String json) throws IOException {
		Files.writeString(mainPath(), json, StandardCharsets.UTF_8);
	}

	private ResponderConfig readMain() {
		try {
			return ResponderConfigJson.read(GSON, Files.readString(mainPath(), StandardCharsets.UTF_8));
		} catch (IOException error) {
			throw new AssertionError(error);
		}
	}

	private byte[] write(String relative, String value) throws IOException {
		Path path = directory.resolve(relative);
		Files.createDirectories(path.getParent());
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		Files.write(path, bytes);
		return bytes;
	}

	private void assertArchived(String relative, byte[] expected) throws IOException {
		assertArrayEquals(expected, Files.readAllBytes(directory
				.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY).resolve("files").resolve(relative)));
		JsonObject manifest = JsonParser.parseString(Files.readString(directory
				.resolve(RetiredServerSupportArchive.ARCHIVE_DIRECTORY).resolve("manifest.json"))).getAsJsonObject();
		assertTrue(manifest.getAsJsonArray("entries").asList().stream()
				.anyMatch(entry -> relative.equals(entry.getAsJsonObject().get("path").getAsString())));
	}
}
