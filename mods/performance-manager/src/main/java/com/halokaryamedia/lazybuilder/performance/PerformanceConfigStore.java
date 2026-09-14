package com.halokaryamedia.lazybuilder.performance;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Owns persistence for Performance Manager policy preferences. */
public final class PerformanceConfigStore {
    private static final String FILE_NAME = "lazybuilder-performance-manager.properties";
    private static final String HEADER = "LazyBuilder Performance Manager preferences";

    private final Path configFile;

    public PerformanceConfigStore(Path configDirectory) {
        this.configFile = configDirectory.resolve(FILE_NAME);
    }

    public PerformancePreferences load() {
        PerformancePreferences defaults = PerformancePreferences.defaults();
        if (!Files.isRegularFile(configFile)) {
            save(defaults);
            return defaults;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        } catch (IOException ignored) {
            return defaults;
        }

        return new PerformancePreferences(
                readBoolean(properties, "background.enabled", defaults.backgroundFpsPolicy()),
                readInt(properties, "background.unfocused_fps", defaults.unfocusedFpsLimit()),
                readInt(properties, "background.minimized_fps", defaults.minimizedFpsLimit())
        );
    }

    public void save(PerformancePreferences preferences) {
        Properties properties = new Properties();
        properties.setProperty("background.enabled", Boolean.toString(preferences.backgroundFpsPolicy()));
        properties.setProperty("background.unfocused_fps", Integer.toString(preferences.unfocusedFpsLimit()));
        properties.setProperty("background.minimized_fps", Integer.toString(preferences.minimizedFpsLimit()));

        Path parent = configFile.getParent();
        Path temporary = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, HEADER);
            }
            try {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignoredCleanup) {
                // Config persistence must never block Minecraft startup.
            }
        }
    }

    public Path configFile() {
        return configFile;
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
