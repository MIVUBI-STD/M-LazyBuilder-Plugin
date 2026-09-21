package com.halokaryamedia.lazybuilder.utility.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

/** Atomic persistence owner for capture preferences. */
public final class CaptureConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("LazyBuilder/Capture/Config");
    private static final String FILE_NAME = "lazybuilder-capture.properties";

    private final Path configFile;

    public CaptureConfigStore(Path configDirectory) {
        this.configFile = configDirectory.resolve(FILE_NAME);
    }

    public CapturePreferences load() {
        CapturePreferences defaults = CapturePreferences.defaults();
        if (!Files.isRegularFile(configFile)) {
            save(defaults);
            return defaults;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        } catch (IOException error) {
            LOGGER.warn("Unable to read capture preferences from {}; using defaults", configFile, error);
            return defaults;
        }

        return new CapturePreferences(
                readEnum(
                        properties.getProperty("screenshot.quality"),
                        CapturePreferences.ScreenshotQuality.class,
                        defaults.screenshotQuality()
                ),
                readEnum(
                        properties.getProperty("video.quality"),
                        CapturePreferences.VideoQuality.class,
                        defaults.videoQuality()
                ),
                readEnum(
                        properties.getProperty("video.resolution"),
                        CapturePreferences.VideoResolution.class,
                        defaults.videoResolution()
                ),
                readEnum(
                        properties.getProperty("video.fps"),
                        CapturePreferences.VideoFrameRate.class,
                        defaults.videoFrameRate()
                ),
                readEnum(
                        properties.getProperty("video.encoder"),
                        CapturePreferences.VideoEncoderMode.class,
                        defaults.videoEncoderMode()
                )
        );
    }

    public void save(CapturePreferences preferences) {
        Properties properties = new Properties();
        properties.setProperty(
                "screenshot.quality",
                preferences.screenshotQuality().name().toLowerCase(Locale.ROOT)
        );
        properties.setProperty(
                "video.quality",
                preferences.videoQuality().name().toLowerCase(Locale.ROOT)
        );
        properties.setProperty(
                "video.resolution",
                preferences.videoResolution().name().toLowerCase(Locale.ROOT)
        );
        properties.setProperty(
                "video.fps",
                preferences.videoFrameRate().name().toLowerCase(Locale.ROOT)
        );
        properties.setProperty(
                "video.encoder",
                preferences.videoEncoderMode().name().toLowerCase(Locale.ROOT)
        );

        Path parent = configFile.getParent();
        Path temporary = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "LazyBuilder Capture preferences");
            }
            try {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            LOGGER.warn("Unable to persist capture preferences to {}", configFile, error);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                LOGGER.debug("Unable to remove temporary capture config {}", temporary, cleanupError);
            }
        }
    }

    Path configFile() {
        return configFile;
    }

    private static <E extends Enum<E>> E readEnum(
            String raw,
            Class<E> type,
            E fallback
    ) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
