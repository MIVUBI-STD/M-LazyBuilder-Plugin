package com.halokaryamedia.lazybuilder.performance.shader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Persistence owner for first-party shader selection and enabled state. */
public final class ShaderRuntimeConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Shader/Config");
    private static final String FILE_NAME = "lazybuilder-shader-runtime.properties";
    private static final String HEADER = "LazyBuilder first-party shader runtime";

    private final Path configFile;

    public ShaderRuntimeConfigStore(Path configDirectory) {
        this.configFile = configDirectory.resolve(FILE_NAME);
    }

    public ShaderRuntimePreferences load() {
        ShaderRuntimePreferences defaults = ShaderRuntimePreferences.defaults();
        if (!Files.isRegularFile(configFile)) return defaults;

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        } catch (IOException error) {
            LOGGER.warn("Unable to read shader runtime preferences from {}; using defaults", configFile, error);
            return defaults;
        }

        String selected = properties.getProperty("shader.selected_pack", "").trim();
        boolean enabled = readBoolean(properties, "shader.enabled", false);

        Map<String, String> optionValues = new LinkedHashMap<>();
        String prefix = "shader.option.";
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) continue;
            String optionKey = key.substring(prefix.length()).trim();
            if (optionKey.isBlank() || !optionKey.contains("::")) continue;
            optionValues.put(optionKey, properties.getProperty(key, "").trim());
        }
        return new ShaderRuntimePreferences(selected, enabled, Map.copyOf(optionValues));
    }

    public void save(ShaderRuntimePreferences preferences) {
        Properties properties = new Properties();
        properties.setProperty("shader.selected_pack", preferences.selectedPackId());
        properties.setProperty("shader.enabled", Boolean.toString(preferences.enabled()));
        for (Map.Entry<String, String> option : preferences.optionValues().entrySet()) {
            properties.setProperty("shader.option." + option.getKey(), option.getValue());
        }

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
        } catch (IOException error) {
            LOGGER.warn("Unable to persist shader runtime preferences to {}", configFile, error);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                LOGGER.debug("Unable to remove temporary shader config {}", temporary, cleanupFailure);
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
}
