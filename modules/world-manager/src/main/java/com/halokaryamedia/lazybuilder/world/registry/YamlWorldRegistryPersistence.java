package com.halokaryamedia.lazybuilder.world.registry;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** YAML-backed registry persistence with atomic publication where supported. */
public final class YamlWorldRegistryPersistence implements WorldRegistryPersistence {
    private static final String WORLDS_PATH = "worlds";

    private final Path registryFile;

    public YamlWorldRegistryPersistence(Path registryFile) {
        this.registryFile = Objects.requireNonNull(registryFile, "registryFile").toAbsolutePath().normalize();
    }

    @Override
    public List<WorldRecord> load() throws IOException {
        if (Files.notExists(registryFile)) {
            return List.of();
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(registryFile.toFile());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("World registry is not valid YAML: " + registryFile, exception);
        }

        ConfigurationSection worlds = yaml.getConfigurationSection(WORLDS_PATH);
        if (worlds == null) {
            return List.of();
        }

        List<WorldRecord> loaded = new ArrayList<>();
        WorldRegistry validation = new WorldRegistry();
        for (String idText : worlds.getKeys(false)) {
            ConfigurationSection section = worlds.getConfigurationSection(idText);
            if (section == null) {
                throw new IOException("Invalid world registry entry: " + idText);
            }

            try {
                WorldRecord record = new WorldRecord(
                        WorldId.parse(idText),
                        requiredString(section, "folder"),
                        requiredString(section, "display-name"),
                        WorldKind.valueOf(requiredString(section, "kind")),
                        WorldLifecycle.valueOf(requiredString(section, "lifecycle")),
                        section.getBoolean("auto-load", false),
                        section.getString("default-game-mode", WorldRecord.DEFAULT_GAME_MODE)
                );
                validation.register(record);
                loaded.add(record);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid world registry entry: " + idText, exception);
            }
        }
        return List.copyOf(loaded);
    }

    @Override
    public void save(List<WorldRecord> worlds) throws IOException {
        Objects.requireNonNull(worlds, "worlds");
        Path parent = registryFile.getParent();
        if (parent == null) {
            throw new IOException("Registry path has no parent: " + registryFile);
        }
        Files.createDirectories(parent);

        YamlConfiguration yaml = new YamlConfiguration();
        for (WorldRecord world : worlds) {
            String base = WORLDS_PATH + "." + world.id();
            yaml.set(base + ".folder", world.folderName());
            yaml.set(base + ".display-name", world.displayName());
            yaml.set(base + ".kind", world.kind().name());
            yaml.set(base + ".lifecycle", world.lifecycle().name());
            yaml.set(base + ".auto-load", world.autoLoad());
            yaml.set(base + ".default-game-mode", world.defaultGameMode());
        }

        Path temporary = parent.resolve(registryFile.getFileName() + ".tmp");
        try {
            yaml.save(temporary.toFile());
            moveIntoPlace(temporary, registryFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String requiredString(ConfigurationSection section, String path) throws IOException {
        String value = section.getString(path);
        if (value == null) {
            throw new IOException("Missing registry property: " + path);
        }
        return value;
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
