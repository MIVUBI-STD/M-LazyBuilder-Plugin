package com.halokaryamedia.lazybuilder.utility;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Owns Utility Manager preference persistence and no feature behavior. */
public final class UtilityConfigStore {
    private static final String FILE_NAME = "lazybuilder-utility-manager.properties";
    private static final String HEADER = "LazyBuilder Utility Manager preferences";

    private final Path configFile;

    public UtilityConfigStore(Path configDirectory) {
        this.configFile = configDirectory.resolve(FILE_NAME);
    }

    public UtilityPreferences load() {
        UtilityPreferences defaults = UtilityPreferences.defaults();
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

        return new UtilityPreferences(
                readBoolean(properties, "window.borderless", defaults.borderlessWindow()),
                readBoolean(properties, "interface.compact_info", defaults.compactInfo()),
                readBoolean(properties, "chat.extended_history", defaults.extendedChatHistory()),
                readBoolean(properties, "chat.keep_draft", defaults.keepChatDraft()),
                readBoolean(properties, "chat.timestamps", defaults.chatTimestamps()),
                readBoolean(properties, "connection.reconnect_button", defaults.reconnectButton()),
                readBoolean(properties, "connection.auto_reconnect", defaults.autoReconnect()),
                readBoolean(properties, "screenshots.organize_by_project", defaults.organizeScreenshotsByProject())
        );
    }

    public void save(UtilityPreferences preferences) {
        Properties properties = new Properties();
        properties.setProperty("window.borderless", Boolean.toString(preferences.borderlessWindow()));
        properties.setProperty("interface.compact_info", Boolean.toString(preferences.compactInfo()));
        properties.setProperty("chat.extended_history", Boolean.toString(preferences.extendedChatHistory()));
        properties.setProperty("chat.keep_draft", Boolean.toString(preferences.keepChatDraft()));
        properties.setProperty("chat.timestamps", Boolean.toString(preferences.chatTimestamps()));
        properties.setProperty("connection.reconnect_button", Boolean.toString(preferences.reconnectButton()));
        properties.setProperty("connection.auto_reconnect", Boolean.toString(preferences.autoReconnect()));
        properties.setProperty(
                "screenshots.organize_by_project",
                Boolean.toString(preferences.organizeScreenshotsByProject())
        );

        Path parent = configFile.getParent();
        Path temporary = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, HEADER);
            }
            try {
                Files.move(
                        temporary,
                        configFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignoredCleanup) {
                // Preference persistence failure must never block Minecraft startup.
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
}
