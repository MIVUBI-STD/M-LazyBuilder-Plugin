package com.halokaryamedia.lazybuilder.world.registry;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** YAML-backed registry persistence with crash-recoverable publication. */
public final class YamlWorldRegistryPersistence implements WorldRegistryPersistence {
    private static final String WORLDS_PATH = "worlds";
    private static final String SCHEMA_PATH = "schema-version";
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_REGISTRY_BYTES = 16L * 1024L * 1024L;

    private final Path registryFile;

    public YamlWorldRegistryPersistence(Path registryFile) {
        this.registryFile = Objects.requireNonNull(registryFile, "registryFile")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public List<WorldRecord> load() throws IOException {
        recoverInterruptedPublish();
        if (Files.notExists(registryFile)) return List.of();
        requireSafeRegularFile(registryFile, "world registry");

        long size = Files.size(registryFile);
        if (size > MAX_REGISTRY_BYTES) {
            throw new IOException("World registry exceeds the " + MAX_REGISTRY_BYTES + " byte safety limit");
        }

        String raw = Files.readString(registryFile, StandardCharsets.UTF_8);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("World registry is not valid YAML: " + registryFile, exception);
        }

        boolean hasSchema = yaml.contains(SCHEMA_PATH);
        if (hasSchema && !yaml.isInt(SCHEMA_PATH)) {
            throw new IOException("World registry schema-version must be an integer");
        }
        int schema = hasSchema ? yaml.getInt(SCHEMA_PATH) : 0;
        if (hasSchema && schema != SCHEMA_VERSION) {
            throw new IOException("World registry schema " + schema + " is unsupported");
        }

        ConfigurationSection worlds = yaml.getConfigurationSection(WORLDS_PATH);
        if (worlds == null) {
            boolean validEmptyLegacy = !hasSchema && raw.isBlank();
            boolean validEmptyCurrent = hasSchema && schema == SCHEMA_VERSION;
            if (!validEmptyLegacy && !validEmptyCurrent) {
                throw new IOException(
                        "World registry is non-empty but missing the required worlds section");
            }
            cleanupRecoveryFiles();
            return List.of();
        }

        List<WorldRecord> loaded = new ArrayList<>();
        WorldRegistry validation = new WorldRegistry();
        for (String idText : worlds.getKeys(false)) {
            ConfigurationSection section = worlds.getConfigurationSection(idText);
            if (section == null) throw new IOException("Invalid world registry entry: " + idText);

            try {
                WorldRecord record = new WorldRecord(
                        WorldId.parse(idText),
                        requiredString(section, "folder"),
                        requiredString(section, "display-name"),
                        WorldKind.valueOf(requiredString(section, "kind")),
                        WorldLifecycle.valueOf(requiredString(section, "lifecycle")),
                        section.getString("default-game-mode", WorldRecord.DEFAULT_GAME_MODE)
                );
                validation.register(record);
                loaded.add(record);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid world registry entry: " + idText, exception);
            }
        }

        cleanupRecoveryFiles();
        return List.copyOf(loaded);
    }

    @Override
    public void save(List<WorldRecord> worlds) throws IOException {
        Objects.requireNonNull(worlds, "worlds");
        Path parent = registryFile.getParent();
        if (parent == null) throw new IOException("Registry path has no parent: " + registryFile);
        Files.createDirectories(parent);
        if (!Files.isDirectory(parent) || Files.isSymbolicLink(parent)) {
            throw new IOException("World registry directory is unsafe: " + parent);
        }

        recoverInterruptedPublish();
        if (Files.exists(previousPath()) && Files.notExists(temporaryPath())) {
            // A stale previous copy after a successful commit is cleanup debt, not
            // ambiguous transaction state. Validate the committed main registry through
            // the normal load path, which removes the stale previous copy only after
            // semantic validation succeeds.
            load();
        }
        if (Files.exists(previousPath()) || Files.exists(temporaryPath())) {
            throw new IOException(
                    "World registry recovery evidence is unresolved; load/reconcile before saving");
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(SCHEMA_PATH, SCHEMA_VERSION);
        yaml.createSection(WORLDS_PATH);
        for (WorldRecord world : worlds) {
            String base = WORLDS_PATH + "." + world.id();
            yaml.set(base + ".folder", world.folderName());
            yaml.set(base + ".display-name", world.displayName());
            yaml.set(base + ".kind", world.kind().name());
            yaml.set(base + ".lifecycle", world.lifecycle().name());
            yaml.set(base + ".default-game-mode", world.defaultGameMode());
        }

        byte[] payload = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_REGISTRY_BYTES) {
            throw new IOException("World registry exceeds the " + MAX_REGISTRY_BYTES + " byte safety limit");
        }

        Path temporary = temporaryPath();
        writeDurably(temporary, payload);

        Path previous = previousPath();
        boolean hadCurrent = Files.exists(registryFile);
        if (hadCurrent) {
            requireSafeRegularFile(registryFile, "world registry");
            moveNoReplace(registryFile, previous);
        }

        try {
            moveNoReplace(temporary, registryFile);
        } catch (IOException publishFailure) {
            if (hadCurrent && Files.exists(previous) && Files.notExists(registryFile)) {
                try {
                    moveNoReplace(previous, registryFile);
                } catch (IOException rollbackFailure) {
                    publishFailure.addSuppressed(rollbackFailure);
                }
            }
            throw publishFailure;
        }

        // Publication is the commit point. Cleanup must never turn a successful
        // registry commit into a reported failure because callers would then roll
        // back filesystem state against already-durable registry truth. Any stale
        // recovery evidence is safe to preserve and will be reconciled on load.
        cleanupAfterCommittedSave(previous, temporary);
    }

    private void recoverInterruptedPublish() throws IOException {
        Path previous = previousPath();
        Path temporary = temporaryPath();

        if (Files.exists(registryFile)) {
            requireSafeRegularFile(registryFile, "world registry");
            if (Files.exists(previous)) requireSafeRegularFile(previous, "previous world registry");
            if (Files.exists(temporary)) requireSafeRegularFile(temporary, "world registry staging file");
            return;
        }

        if (Files.exists(previous)) {
            requireSafeRegularFile(previous, "previous world registry");
            moveNoReplace(previous, registryFile);
            return;
        }

        if (Files.exists(temporary)) {
            requireSafeRegularFile(temporary, "world registry staging file");
            moveNoReplace(temporary, registryFile);
        }
    }

    private static void cleanupAfterCommittedSave(Path previous, Path temporary) {
        try { Files.deleteIfExists(previous); }
        catch (IOException ignored) { }
        try { Files.deleteIfExists(temporary); }
        catch (IOException ignored) { }
    }

    private void cleanupRecoveryFiles() throws IOException {
        Files.deleteIfExists(previousPath());
        Files.deleteIfExists(temporaryPath());
    }

    private Path temporaryPath() {
        return registryFile.resolveSibling(registryFile.getFileName() + ".tmp");
    }

    private Path previousPath() {
        return registryFile.resolveSibling(registryFile.getFileName() + ".previous");
    }

    private static void writeDurably(Path path, byte[] payload) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void requireSafeRegularFile(Path path, String label) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IOException(label + " is unsafe: " + path);
        }
    }

    private static String requiredString(ConfigurationSection section, String path) throws IOException {
        String value = section.getString(path);
        if (value == null) throw new IOException("Missing registry property: " + path);
        return value;
    }

    private static void moveNoReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }
}
