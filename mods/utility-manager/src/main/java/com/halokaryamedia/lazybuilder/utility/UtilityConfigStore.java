package com.halokaryamedia.lazybuilder.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Owns Utility Manager preference persistence and no feature behavior. */
public final class UtilityConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Utility/Config");
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
        } catch (IOException exception) {
            LOGGER.warn("Unable to read Utility Manager preferences from {}; using safe defaults", configFile, exception);
            return defaults;
        }

        return new UtilityPreferences(
                readBoolean(properties, "window.borderless", defaults.borderlessWindow()),
                readBoolean(properties, "chat.extended_history", defaults.extendedChatHistory()),
                readBoolean(properties, "chat.keep_draft", defaults.keepChatDraft()),
                readBoolean(properties, "chat.search", defaults.chatSearch()),
                readBoolean(properties, "chat.timestamps", defaults.chatTimestamps()),
                readBoolean(properties, "chat.hide_signing_indicators", defaults.hideChatSigningIndicators()),
                readBoolean(properties, "chat.hide_report_button", defaults.hideChatReportButton()),
                readBoolean(properties, "accessibility.suppress_narrator", defaults.suppressNarrator()),
                readBoolean(properties, "connection.reconnect_button", defaults.reconnectButton()),
                readBooleanWithLegacyAlias(
                        properties,
                        "screenshots.contextual_names",
                        "screenshots.organize_by_project",
                        defaults.contextualScreenshotNames()
                ),
                readBoolean(properties, "inventory.instant_creative_search", defaults.instantCreativeSearch()),
                readBoolean(properties, "hud.compact_debug", defaults.compactDebugHud())
        );
    }

    public void save(UtilityPreferences preferences) {
        Properties properties = new Properties();
        properties.setProperty("window.borderless", Boolean.toString(preferences.borderlessWindow()));
        properties.setProperty("chat.extended_history", Boolean.toString(preferences.extendedChatHistory()));
        properties.setProperty("chat.keep_draft", Boolean.toString(preferences.keepChatDraft()));
        properties.setProperty("chat.search", Boolean.toString(preferences.chatSearch()));
        properties.setProperty("chat.timestamps", Boolean.toString(preferences.chatTimestamps()));
        properties.setProperty(
                "chat.hide_signing_indicators",
                Boolean.toString(preferences.hideChatSigningIndicators())
        );
        properties.setProperty(
                "chat.hide_report_button",
                Boolean.toString(preferences.hideChatReportButton())
        );
        properties.setProperty(
                "accessibility.suppress_narrator",
                Boolean.toString(preferences.suppressNarrator())
        );
        properties.setProperty("connection.reconnect_button", Boolean.toString(preferences.reconnectButton()));
        properties.setProperty(
                "screenshots.contextual_names",
                Boolean.toString(preferences.contextualScreenshotNames())
        );
        properties.setProperty(
                "inventory.instant_creative_search",
                Boolean.toString(preferences.instantCreativeSearch())
        );
        properties.setProperty("hud.compact_debug", Boolean.toString(preferences.compactDebugHud()));

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
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to persist Utility Manager preferences to {}; keeping runtime preferences active", configFile, exception);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                LOGGER.debug("Unable to remove temporary Utility Manager preference file {}", temporary, cleanupFailure);
            }
        }
    }

    public Path configFile() {
        return configFile;
    }

    private static boolean readBooleanWithLegacyAlias(
            Properties properties,
            String key,
            String legacyKey,
            boolean fallback
    ) {
        if (properties.containsKey(key)) return readBoolean(properties, key, fallback);
        return readBoolean(properties, legacyKey, fallback);
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        return fallback;
    }
}
