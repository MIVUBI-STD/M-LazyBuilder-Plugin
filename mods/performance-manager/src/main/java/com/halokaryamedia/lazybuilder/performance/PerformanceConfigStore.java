package com.halokaryamedia.lazybuilder.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Owns persistence for Performance Manager policy preferences. */
public final class PerformanceConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Performance/Config");
    private static final String FILE_NAME = "lazybuilder-performance-manager.properties";
    private static final String HEADER = "LazyBuilder Performance Manager preferences";
    private static final long MAX_CONFIG_BYTES = 64L * 1024L;

    private final Path configFile;

    public PerformanceConfigStore(Path configDirectory) {
        this.configFile = configDirectory.resolve(FILE_NAME);
    }

    public PerformancePreferences load() {
        PerformancePreferences defaults = PerformancePreferences.defaults();
        if (!Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
            save(defaults);
            return defaults;
        }
        if (!Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
            LOGGER.warn("Unsafe Performance Manager preference file {}; using defaults without changing it", configFile);
            return defaults;
        }

        Properties properties = new Properties();
        try {
            if (Files.size(configFile) > MAX_CONFIG_BYTES) {
                LOGGER.warn("Oversized Performance Manager preference file {}; preserving it and using defaults",
                        configFile);
                return defaults;
            }
        } catch (IOException inspectionFailure) {
            LOGGER.warn("Could not inspect Performance Manager preferences {}; using defaults",
                    configFile, inspectionFailure);
            return defaults;
        }
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        } catch (IOException exception) {
            LOGGER.warn("Unable to read Performance Manager preferences from {}; using safe defaults", configFile, exception);
            return defaults;
        }

        return new PerformancePreferences(
                readBoolean(properties, "background.enabled", defaults.backgroundFpsPolicy()),
                readInt(properties, "background.unfocused_fps", defaults.unfocusedFpsLimit()),
                readInt(properties, "background.minimized_fps", defaults.minimizedFpsLimit()),
                readBoolean(properties, "culling.entities", defaults.entityCulling()),
                readBoolean(properties, "culling.block_entities", defaults.blockEntityCulling()),
                readBoolean(properties, "rendering.optimizations", defaults.renderingOptimizations()),
                readBoolean(properties, "memory.optimizations", defaults.memoryOptimizations())
        );
    }

    public void save(PerformancePreferences preferences) {
        Properties properties = new Properties();
        properties.setProperty("background.enabled", Boolean.toString(preferences.backgroundFpsPolicy()));
        properties.setProperty("background.unfocused_fps", Integer.toString(preferences.unfocusedFpsLimit()));
        properties.setProperty("background.minimized_fps", Integer.toString(preferences.minimizedFpsLimit()));
        properties.setProperty("culling.entities", Boolean.toString(preferences.entityCulling()));
        properties.setProperty("culling.block_entities", Boolean.toString(preferences.blockEntityCulling()));
        properties.setProperty("rendering.optimizations", Boolean.toString(preferences.renderingOptimizations()));
        properties.setProperty("memory.optimizations", Boolean.toString(preferences.memoryOptimizations()));

        Path parent = configFile.getParent();
        Path temporary = null;
        try {
            if (parent == null) throw new IOException("Performance preferences have no config directory");
            Files.createDirectories(parent);
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Performance preferences directory is unsafe: " + parent);
            }
            if (Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing unsafe Performance Manager preference target: " + configFile);
            }
            temporary = Files.createTempFile(parent, FILE_NAME + ".", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, HEADER);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to persist Performance Manager preferences to {}; keeping runtime preferences active",
                    configFile, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    LOGGER.debug("Unable to remove temporary Performance Manager preference file {}",
                            temporary, cleanupFailure);
                }
            }
        }
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        return fallback;
    }

    private static int readInt(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
